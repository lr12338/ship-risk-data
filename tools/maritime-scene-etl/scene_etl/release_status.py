from __future__ import annotations

from typing import Any


RELEASE_STATUSES = (
    "RAW_VERIFIED",
    "GEOMETRY_VALIDATED",
    "GLOBAL_CORE_VALIDATED",
    "AUTO_CROSS_VALIDATED",
    "SHADOW_READY",
    "ENFORCE_CANDIDATE",
    "ENFORCE_READY",
)


def derive_release_status(
    dataset_statuses: list[dict[str, Any]],
    automatic_validation: dict[str, Any] | None,
) -> str:
    if any(item.get("status") in {"MISSING", "INVALID", "FAILED"} for item in dataset_statuses):
        return "RAW_VERIFIED"
    if automatic_validation is None:
        return "GEOMETRY_VALIDATED"
    if automatic_validation.get("status") in {"FAILED", "FAIL"}:
        return "GEOMETRY_VALIDATED"
    return str(automatic_validation.get("releaseStatus", "AUTO_CROSS_VALIDATED"))


def can_promote(current: str, target: str) -> bool:
    if target not in RELEASE_STATUSES or target in {"ENFORCE_CANDIDATE", "ENFORCE_READY"}:
        return False
    try:
        return RELEASE_STATUSES.index(target) >= RELEASE_STATUSES.index(current)
    except ValueError:
        return False
