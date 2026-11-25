from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.api import create_app
from app.common.config import Settings


@pytest.fixture
def settings(tmp_path) -> Settings:
    return Settings(
        environment="test",
        internal_api_key="test-internal-key",
        database_url=f"sqlite:///{tmp_path / 'gov.db'}",
        backend_base_url="http://backend.test",
        llm_provider="none",
    )


@pytest.fixture
def client(settings) -> TestClient:
    app = create_app(settings)
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def auth_headers(settings) -> dict[str, str]:
    return {"X-Internal-Api-Key": settings.internal_api_key}
