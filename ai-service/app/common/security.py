from __future__ import annotations

import hmac

from fastapi import Header, HTTPException, Request, status

API_KEY_HEADER = "X-Internal-Api-Key"


def require_internal_key(request: Request, x_internal_api_key: str | None = Header(default=None, alias=API_KEY_HEADER)) -> str:
    """Rejects any call that does not carry the shared internal API key (constant-time comparison)."""
    expected: str = request.app.state.settings.internal_api_key
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key.encode(), expected.encode()):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail={
            "error_code": "INVALID_INTERNAL_API_KEY",
            "message": f"A valid {API_KEY_HEADER} header is required for internal AI endpoints",
        })
    return request.headers.get("X-Caller-Service", "spring-boot-backend")
