from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app import __version__
from app.anomaly.router import router as anomaly_router
from app.assistant.router import router as assistant_router
from app.common.config import Settings, get_settings
from app.credit.router import router as credit_router
from app.forecasting.router import router as forecasting_router
from app.fraud.router import router as fraud_router
from app.governance.registry import list_models
from app.governance.router import router as governance_router
from app.governance.store import GovernanceStore
from app.optimisation.router import router as optimisation_router

logger = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if settings.database_url.startswith("sqlite:///./"):
            Path("data").mkdir(parents=True, exist_ok=True)
        app.state.settings = settings
        app.state.governance = GovernanceStore(settings.database_url)
        logger.info("AI service ready (env=%s, llm=%s)", settings.environment, settings.llm_enabled)
        yield

    app = FastAPI(
        title="Agent Banking AI Intelligence Service",
        description=(
            "Internal FastAPI microservice for fraud scoring, credit scoring, float forecasting, "
            "anomaly detection, float redistribution and a read-only LLM assistant. "
            "All `/internal/ai/v1/**` endpoints require the `X-Internal-Api-Key` header. "
            "Predictions are labelled as PREDICTION and are not verified ledger facts."
        ),
        version=__version__,
        lifespan=lifespan,
    )

    @app.exception_handler(StarletteHTTPException)
    async def http_error(_request: Request, exc: StarletteHTTPException) -> JSONResponse:
        detail: Any = exc.detail
        if isinstance(detail, dict):
            body = detail
        else:
            body = {"error_code": "HTTP_ERROR", "message": str(detail)}
        return JSONResponse(status_code=exc.status_code, content=body)

    @app.exception_handler(RequestValidationError)
    async def validation_error(_request: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(status_code=422, content={
            "error_code": "VALIDATION_ERROR",
            "message": "Request validation failed",
            "details": exc.errors(),
        })

    @app.get("/health", tags=["Health"], summary="Liveness and model inventory (public)")
    def health() -> dict[str, Any]:
        return {
            "status": "UP",
            "version": __version__,
            "environment": settings.environment,
            "llm_enabled": settings.llm_enabled,
            "using_default_internal_api_key": settings.using_default_key,
            "models": [m.model_dump() for m in list_models()],
        }

    app.include_router(fraud_router)
    app.include_router(credit_router)
    app.include_router(forecasting_router)
    app.include_router(anomaly_router)
    app.include_router(optimisation_router)
    app.include_router(assistant_router)
    app.include_router(governance_router)
    return app
