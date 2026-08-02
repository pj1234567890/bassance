import os
import tempfile
import torch
import numpy as np
import torchaudio
import torchaudio.transforms as T
from fastapi import FastAPI, UploadFile, File
from torchvision import models
import torch.nn as nn

# ---- CPU SAFETY LIMITS ----
torch.set_num_threads(2)
torch.set_num_interop_threads(1)

app = FastAPI()

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

MODEL_PATH = "spec_cnn_v1.pt"
model = SpecResNet(len(CLASSES))
state = torch.load(MODEL_PATH, map_location=device)
state_dict = state["state_dict"] if isinstance(state, dict) and "state_dict" in state else state
model.load_state_dict(state_dict)
model.to(device)
model.eval()


def wav_to_spec(path):
    wav, sr = torchaudio.load(path)

    if sr != TARGET_SR:
        wav = torchaudio.functional.resample(wav, sr, TARGET_SR)

    if wav.shape[0] > 1:
        wav = wav.mean(dim=0, keepdim=True)

    max_len = TARGET_SR * MAX_SECONDS
    if wav.shape[1] > max_len:
        wav = wav[:, :max_len]

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


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    # cross-platform temp file (works on Windows)
    with tempfile.NamedTemporaryFile(delete=False, suffix=file.filename) as tmp:
        tmp.write(await file.read())
        temp_path = tmp.name

    try:
        spec = wav_to_spec(temp_path)
        spec = spec.unsqueeze(0).to(device)

        with torch.inference_mode():
            logits = model(spec)
            probs = torch.softmax(logits, dim=1)[0]
            top_p, top_i = torch.topk(probs, k=3)

        result = []
        for p, i in zip(top_p.tolist(), top_i.tolist()):
            result.append({
                "genre": CLASSES[i],
                "confidence": round(p, 4)
            })

        return {"predictions": result}

    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)
