from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_DEV_API_KEY = "local-dev-internal-key-change-me"


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (prefix ``AI_``)."""

    model_config = SettingsConfigDict(env_prefix="AI_", env_file=".env", extra="ignore")

    environment: str = "local"
    internal_api_key: str = DEFAULT_DEV_API_KEY
    database_url: str = "sqlite:///./data/ai_governance.db"
    timezone: str = "Africa/Harare"

    backend_base_url: str = "http://localhost:8080"
    backend_timeout_seconds: float = 8.0

    llm_provider: str = Field(default="none", description="'none' or 'openai' (any OpenAI-compatible endpoint)")
    llm_api_key: str = ""
    llm_base_url: str = "https://api.openai.com/v1"
    llm_model: str = "gpt-4o-mini"
    llm_timeout_seconds: float = 30.0
    llm_max_tool_rounds: int = 4

    @property
    def llm_enabled(self) -> bool:
        return self.llm_provider.lower() == "openai" and bool(self.llm_api_key)

    @property
    def using_default_key(self) -> bool:
        return self.internal_api_key == DEFAULT_DEV_API_KEY


@lru_cache
def get_settings() -> Settings:
    return Settings()
