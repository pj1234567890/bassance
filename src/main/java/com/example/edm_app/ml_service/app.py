import asyncio
import json
import logging
import os
import secrets
import subprocess
import tempfile
from pathlib import Path

import torch
import numpy as np
import torchaudio.transforms as T
from fastapi import FastAPI, UploadFile, File, Header, HTTPException
from fastapi.concurrency import run_in_threadpool
from torchvision import models
import torch.nn as nn

# ---- CPU SAFETY LIMITS ----
torch.set_num_threads(2)
torch.set_num_interop_threads(1)

app = FastAPI()
logger = logging.getLogger(__name__)
MAX_UPLOAD_BYTES = int(os.getenv("MAX_UPLOAD_BYTES", str(50 * 1024 * 1024)))
ML_SERVICE_TOKEN = os.getenv("ML_SERVICE_TOKEN", "")
if len(ML_SERVICE_TOKEN) < 32:
    raise RuntimeError("ML_SERVICE_TOKEN must be configured with at least 32 characters")
INFERENCE_SLOTS = asyncio.Semaphore(1)
ALLOWED_SUFFIXES = {".mp3", ".wav", ".flac", ".ogg", ".m4a"}
AUDIO_DEMUXERS = {".mp3": "mp3", ".wav": "wav", ".flac": "flac", ".ogg": "ogg", ".m4a": "mov"}

# ===== Model Definition =====

class SpecResNet(nn.Module):
    def __init__(self, n_classes):
        super().__init__()
        self.backbone = models.resnet18(weights=None)
        self.backbone.conv1 = nn.Conv2d(
            1, 64, kernel_size=7, stride=2, padding=3, bias=False
        )
        in_feats = self.backbone.fc.in_features
        self.backbone.fc = nn.Sequential(
            nn.Linear(in_feats, 512),
            nn.ReLU(inplace=True),
            nn.Dropout(0.5),
            nn.Linear(512, n_classes),
        )

    def forward(self, x):
        return self.backbone(x)


CLASSES = [
    "ambient_downtempo",
    "dnb",
    "drumstep",
    "dubstep",
    "future_garage",
    "glitch_moombah",
    "hardcore_hard_dance",
    "hardstyle",
    "house",
    "psytrance",
    "synthwave_synthpop",
    "techno",
    "trance",
    "trap_future_bass",
]

TARGET_SR = 22050
N_MELS = 128
HOP_LEN = 512
WIN_LEN = 1024
CROP_T = 256
MAX_SECONDS = 30

mel = T.MelSpectrogram(
    sample_rate=TARGET_SR,
    n_fft=WIN_LEN,
    hop_length=HOP_LEN,
    win_length=WIN_LEN,
    n_mels=N_MELS,
)
amp_to_db = T.AmplitudeToDB()

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# ===== Load Model Once =====

MODEL_PATH = Path(os.getenv("MODEL_PATH", str(Path(__file__).with_name("spec_cnn_v1.pt"))))
model = SpecResNet(len(CLASSES))
state = torch.load(MODEL_PATH, map_location=device, weights_only=True)
state_dict = state["state_dict"] if isinstance(state, dict) and "state_dict" in state else state
model.load_state_dict(state_dict)
model.to(device)
model.eval()


def wav_to_spec(path):
    suffix = Path(path).suffix.lower()
    demuxer = AUDIO_DEMUXERS.get(suffix)
    if demuxer is None:
        raise ValueError("Unsupported audio format")

    probe = subprocess.run(
        [os.getenv("FFPROBE_BINARY", "ffprobe"), "-v", "error", "-f", demuxer, "-select_streams", "a:0",
         "-protocol_whitelist", "file,pipe", "-show_entries",
         "stream=sample_rate,channels,duration:format=duration", "-of", "json", path],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=10,
    )
    metadata = json.loads(probe.stdout)
    streams = metadata.get("streams", [])
    if not streams:
        raise ValueError("No audio stream found")
    sample_rate = int(streams[0].get("sample_rate", 0))
    num_channels = int(streams[0].get("channels", 0))
    duration_value = streams[0].get("duration") or metadata.get("format", {}).get("duration")
    duration = float(duration_value) if duration_value is not None else None
    if sample_rate <= 0 or sample_rate > 192000 or num_channels <= 0 or num_channels > 8:
        raise ValueError("Unsupported audio metadata")
    if duration is None or duration <= 0:
        raise ValueError("Audio duration could not be determined")
    if duration > 900:
        raise ValueError("Audio is longer than the supported 15-minute maximum")

    decoded = subprocess.run(
        [os.getenv("FFMPEG_BINARY", "ffmpeg"), "-nostdin", "-v", "error",
         "-protocol_whitelist", "file,pipe", "-threads", "1", "-f", demuxer,
         "-i", path, "-map", "0:a:0", "-t", str(MAX_SECONDS), "-ac", "1",
         "-ar", str(TARGET_SR), "-f", "f32le", "-acodec", "pcm_f32le", "pipe:1"],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=20,
    )
    samples = np.frombuffer(decoded.stdout, dtype="<f4").copy()
    if samples.size == 0:
        raise ValueError("Audio stream is empty")
    wav = torch.from_numpy(samples).unsqueeze(0)

    spec = mel(wav)
    spec = amp_to_db(spec)
    spec = spec.squeeze(0).numpy().astype("float32")

    m, s = spec.mean(), spec.std()
    if s > 0:
        spec = (spec - m) / s

    if spec.shape[1] >= CROP_T:
        start = (spec.shape[1] - CROP_T) // 2
        spec = spec[:, start:start + CROP_T]
    else:
        pad = CROP_T - spec.shape[1]
        spec = np.pad(spec, ((0, 0), (0, pad)), mode="constant")

    spec = torch.from_numpy(spec).unsqueeze(0)
    return spec


def classify(temp_path):
    spec = wav_to_spec(temp_path)
    spec = spec.unsqueeze(0).to(device)

    with torch.inference_mode():
        logits = model(spec)
        probs = torch.softmax(logits, dim=1)[0]
        top_p, top_i = torch.topk(probs, k=3)

    return {
        "predictions": [
            {"genre": CLASSES[i], "confidence": round(p, 4)}
            for p, i in zip(top_p.tolist(), top_i.tolist())
        ]
    }


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/predict")
async def predict(
    file: UploadFile = File(...),
    service_token: str | None = Header(default=None, alias="X-ML-Service-Token"),
):
    if service_token is None or not secrets.compare_digest(service_token, ML_SERVICE_TOKEN):
        raise HTTPException(status_code=401, detail="Unauthorized")

    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in ALLOWED_SUFFIXES:
        raise HTTPException(status_code=415, detail="Unsupported audio format")

    if file.size is not None and file.size > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Audio file exceeds the upload limit")

    try:
        await asyncio.wait_for(INFERENCE_SLOTS.acquire(), timeout=0.1)
    except TimeoutError:
        raise HTTPException(status_code=429, detail="Audio service is busy; retry shortly")

    temp_path = None
    try:
        total = 0
        with tempfile.NamedTemporaryFile(mode="wb", prefix="edm-ml-", suffix=suffix, delete=False) as tmp:
            temp_path = tmp.name
            while chunk := await file.read(1024 * 1024):
                total += len(chunk)
                if total > MAX_UPLOAD_BYTES:
                    raise HTTPException(status_code=413, detail="Audio file exceeds the upload limit")
                tmp.write(chunk)
        if total == 0:
            raise HTTPException(status_code=400, detail="Audio file is empty")
        try:
            return await run_in_threadpool(classify, temp_path)
        except Exception as error:
            raise HTTPException(status_code=400, detail="Audio file could not be decoded") from error
    finally:
        await file.close()
        INFERENCE_SLOTS.release()
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                logger.exception("Failed to remove temporary audio file")
