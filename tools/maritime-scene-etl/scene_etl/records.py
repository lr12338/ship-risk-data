from __future__ import annotations

from typing import Any

from .geometry import geometry_wkb_hex


def scene_record(
    scene_type: str,
    source_name: str,
    source_version: str,
    source_feature_id: str,
    geometry,
    geometry_status: str,
    authority_level: str,
    confidence: str,
    properties: dict[str, Any],
    record_kind: str = "scene",
) -> dict[str, Any]:
    return {
        "sceneType": scene_type,
        "sourceName": source_name,
        "sourceVersion": source_version,
        "sourceFeatureId": str(source_feature_id),
        "authorityLevel": authority_level,
        "confidence": confidence,
        "geometryStatus": geometry_status,
        "geometryWkbHex": geometry_wkb_hex(geometry),
        "properties": properties,
        "recordKind": record_kind,
    }


def quarantined_record(
    source_name: str,
    source_version: str,
    source_feature_id: str,
    geometry,
    properties: dict[str, Any],
) -> dict[str, Any]:
    return scene_record(
        "quarantined_geometry",
        source_name,
        source_version,
        source_feature_id,
        geometry,
        "QUARANTINED",
        "REVIEW_REQUIRED",
        "LOW",
        properties,
    )
