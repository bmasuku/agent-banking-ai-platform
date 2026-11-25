"""GET-only Spring Boot client. Every call carries the user's Bearer token and the internal API key."""
from __future__ import annotations

from typing import Any
from urllib.parse import urlencode

import httpx

from app.common.config import Settings


class BackendError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


class BackendClient:
    def __init__(self, settings: Settings, authorization: str, *, client: httpx.Client | None = None):
        self._base = settings.backend_base_url.rstrip("/")
        self._timeout = settings.backend_timeout_seconds
        self._headers = {
            "Authorization": authorization if authorization.lower().startswith("bearer ")
            else f"Bearer {authorization}",
            "X-Internal-Api-Key": settings.internal_api_key,
            "X-Caller-Service": "ai-assistant",
            "Accept": "application/json",
        }
        self._owns = client is None
        self._client = client or httpx.Client(timeout=self._timeout)

    def close(self) -> None:
        if self._owns:
            self._client.close()

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        if not path.startswith("/"):
            raise BackendError(400, "Tool path must be absolute")
        url = f"{self._base}{path}"
        if params:
            cleaned = {k: v for k, v in params.items() if v is not None and v != ""}
            if cleaned:
                url = f"{url}?{urlencode(cleaned, doseq=True)}"
        try:
            response = self._client.get(url, headers=self._headers)
        except httpx.HTTPError as exc:
            raise BackendError(503, f"Backend unreachable: {exc}") from exc
        if response.status_code >= 400:
            detail = response.text[:300]
            raise BackendError(response.status_code, f"Backend returned {response.status_code}: {detail}")
        if not response.content:
            return None
        return response.json()
