from __future__ import annotations

import re
from typing import Any

from pydantic import AliasChoices, Field


def jackson_snake(name: str) -> str:
    """How Jackson's SNAKE_CASE strategy writes a field whose Java name contains digits (``count_1h`` -> ``count1h``)."""
    return re.sub(r"_(\d)", r"\1", name)


def field(name: str, default: Any = ..., **kwargs: Any) -> Any:
    """Field accepting both the clean snake_case name and the Spring Boot (Jackson) spelling."""
    choices = [name]
    if jackson_snake(name) != name:
        choices.append(jackson_snake(name))
    return Field(default, validation_alias=AliasChoices(*choices), **kwargs)
