import os
from contextlib import asynccontextmanager

import joblib
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, field_validator


MODEL_PATH = os.getenv("MODEL_PATH", "./model/ticket_classifier_calibrated.joblib")
CONFIDENCE_THRESHOLD = float(os.getenv("ML_CONFIDENCE_THRESHOLD", "0.80"))


class PredictionRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=20000)

    @field_validator("text")
    @classmethod
    def text_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("text must not be blank")
        return value


class PredictionResponse(BaseModel):
    category: str
    confidence: float = Field(..., ge=0.0, le=1.0)


def load_model(path: str):
    return joblib.load(path)


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model = None
    app.state.model_error = None
    try:
        app.state.model = load_model(MODEL_PATH)
    except Exception as error:
        app.state.model_error = f"Unable to load model artifact: {error}"
    yield


app = FastAPI(title="IT Ticket Classifier", version="4.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    if app.state.model is None:
        raise HTTPException(status_code=503, detail=app.state.model_error or "Model is not loaded")
    return {"status": "ok", "model_loaded": True, "confidence_threshold": CONFIDENCE_THRESHOLD}


@app.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest):
    if app.state.model is None:
        raise HTTPException(status_code=503, detail=app.state.model_error or "Model is not loaded")
    try:
        model = app.state.model
        category = str(model.predict([request.text])[0])
        probabilities = model.predict_proba([request.text])[0]
        confidence = float(max(probabilities))
        return PredictionResponse(category=category, confidence=confidence)
    except Exception as error:
        raise HTTPException(status_code=500, detail="Prediction failed") from error
