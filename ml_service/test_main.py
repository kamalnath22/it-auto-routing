from unittest.mock import patch

from fastapi.testclient import TestClient

import main


class FakeModel:
    def predict(self, texts):
        return ["Access"]

    def predict_proba(self, texts):
        return [[0.02, 0.91, 0.07]]


def test_health_and_predict(monkeypatch):
    monkeypatch.setenv("MODEL_PATH", "./fake.joblib")
    with patch.object(main, "load_model", return_value=FakeModel()):
        with TestClient(main.app) as client:
            health = client.get("/health")
            prediction = client.post("/predict", json={"text": "VPN cannot connect"})

    assert health.status_code == 200
    assert health.json()["model_loaded"] is True
    assert prediction.status_code == 200
    assert prediction.json() == {"category": "Access", "confidence": 0.91}


def test_empty_text_is_rejected():
    with patch.object(main, "load_model", return_value=FakeModel()):
        with TestClient(main.app) as client:
            response = client.post("/predict", json={"text": ""})

    assert response.status_code == 422


def test_missing_model_is_predictable(monkeypatch):
    monkeypatch.setenv("MODEL_PATH", "./missing.joblib")
    with patch.object(main, "load_model", side_effect=FileNotFoundError("missing")):
        with TestClient(main.app) as client:
            health = client.get("/health")
            prediction = client.post("/predict", json={"text": "VPN cannot connect"})

    assert health.status_code == 503
    assert prediction.status_code == 503
