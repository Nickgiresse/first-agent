FROM python:3.11-slim

WORKDIR /srv

# opencv-contrib-python (dépendance de mediapipe, non-headless) a besoin de libGL/libglib au runtime.
# tesseract-ocr : binaire seul, les packs de langue viennent de weights/tessdata (pas des paquets
# apt) pour garantir la même version FR/EN qu'en local.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 curl tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Modèle de détection de visage (MediaPipe Tasks API) + packs de langue Tesseract : non bundlés
# dans les paquets pip/apt, préchargés au build pour un démarrage rapide et reproductible.
RUN mkdir -p weights/tessdata && \
    curl -fsSL -o weights/blaze_face_short_range.tflite \
        https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite && \
    curl -fsSL -o weights/face_landmarker.task \
        https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task && \
    curl -fsSL -o weights/tessdata/fra.traineddata \
        https://github.com/tesseract-ocr/tessdata_fast/raw/main/fra.traineddata && \
    curl -fsSL -o weights/tessdata/eng.traineddata \
        https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata && \
    curl -fsSL -o weights/w600k_r50.onnx \
        https://huggingface.co/public-data/insightface/resolve/main/models/buffalo_l/w600k_r50.onnx

ENV TESSERACT_CMD=/usr/bin/tesseract

COPY app ./app

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
