# AI Intelligence Service

Independent FastAPI microservice for the Agent Banking platform. It scores fraud and credit risk, forecasts float demand, detects anomalies, recommends float redistribution, and powers a read-only assistant. Every prediction is persisted with model version, confidence and explanation; none of the serving models claim to have been trained on production outcomes.

## Capabilities

| Endpoint | Method | Notes |
| --- | --- | --- |
| `GET /health` | public | Liveness + model inventory |
| `POST /internal/ai/v1/fraud/evaluate` | internal key | Deterministic typology rules (0–100) |
| `POST /internal/ai/v1/credit/score` | internal key | Scorecard 0–1000 + APPROVED/REJECTED/MANUAL_REVIEW |
| `POST /internal/ai/v1/float/forecast` | internal key | Statistical hour-of-day forecaster |
| `POST /internal/ai/v1/float/forecast/area` | internal key | Area roll-up |
| `POST /internal/ai/v1/anomaly/detect` | internal key | Robust z / Poisson / Isolation Forest |
| `POST /internal/ai/v1/anomaly/peer-comparison` | internal key | Peer-group outliers |
| `POST /internal/ai/v1/float/optimise` | internal key | LP peer swaps + pool top-ups |
| `POST /internal/ai/v1/assistant/chat` | internal key | Read-only tools via Spring Boot |
| `GET /internal/ai/v1/governance/**` | internal key | Models, predictions, human decisions |

All `/internal/**` routes require header `X-Internal-Api-Key`.

## Run locally

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn main:app --reload --port 8000
```

Default internal key: `local-dev-internal-key-change-me` (override with `AI_INTERNAL_API_KEY`).

Optional LLM assistant (OpenAI-compatible):

```bash
export AI_LLM_PROVIDER=openai
export AI_LLM_API_KEY=sk-...
# optional: AI_LLM_BASE_URL, AI_LLM_MODEL
```

Without an LLM key the assistant uses a deterministic intent router and says so in the response.

## Tests and evaluation

```bash
pytest
python -m training.run_all
```

Evaluation scripts write JSON under `models/evaluation/`. The optional `fraud-challenger` artefact is trained on synthetic data only and is **not served**.

## Docker

```bash
docker build -t agent-banking-ai .
docker run --rm -p 8000:8000 \
  -e AI_INTERNAL_API_KEY=local-dev-internal-key-change-me \
  -e AI_BACKEND_BASE_URL=http://host.docker.internal:8080 \
  agent-banking-ai
```

## Governance rules

- Every response carries a `governance` block with `data_classification: PREDICTION`.
- `is_trained_model` is `false` for all serving models.
- Human decisions can be posted against a `prediction_id`.
- The assistant never executes transactions, approves advances, changes balances or classifies fraud; its tools are GET-only and use the caller's JWT so Spring Boot enforces RBAC.
