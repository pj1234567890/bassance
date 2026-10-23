FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    MODEL_PATH=/app/spec_cnn_v1.pt \
    PORT=8001

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home appuser

WORKDIR /app
COPY requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu \
      torch==2.9.1 torchvision==0.24.1 torchaudio==2.9.1 \
    && pip install --no-cache-dir -r requirements.txt

COPY --chown=10001:10001 app.py spec_cnn_v1.pt ./
USER 10001:10001
EXPOSE 8001
CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8001} --workers 1"]
