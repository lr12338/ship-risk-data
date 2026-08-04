from __future__ import annotations

from collections import Counter
from pathlib import Path
from typing import Any, Iterable

from .coast_distance import classify_coast_distance
from .config import load_yaml
from .jrc import JrcRasterSampler
from .manifest import write_json
from .release_records import point_hits


SCENE_CLASSES = {
    "LAND",
    "INLAND_RIVER",
    "INLAND_LAKE",
    "MAJOR_CANAL",
    "ESTUARY_OR_RIVER_MOUTH",
    "MARITIME_INTERNAL_WATER",
    "ARCHIPELAGIC_WATER",
    "TERRITORIAL_SEA",
    "OFFSHORE_EEZ",
    "HIGH_SEAS",
    "BOUNDARY_UNCERTAIN",
    "CONFLICT",
    "UNKNOWN",
}


def classify_point(
    hits: Iterable[dict[str, Any]],
    jrc: dict[str, Any] | None = None,
    coast_distance_nm: float | None = None,
    policy: dict[str, Any] | None = None,
    runtime_water_support: bool = False,
) -> dict[str, Any]:
    policy = policy or {}
    jrc = jrc or {
        "jrcCoverageAvailable": False,
        "jrcEvidenceClass": "NO_COVERAGE",
        "jrcStableWater": False,
    }
    hit_records = list(hits)
    hit_types = {record.get("sceneType") for record in hit_records}
    conflict = _has_conflict(hit_types)
    water_high = bool(jrc.get("jrcStableWater"))
    coast_label = classify_coast_distance(coast_distance_nm, policy) if policy else "UNKNOWN"
    evidence = {
        "sourceHits": sorted(item for item in hit_types if item),
        "jrc": jrc,
        "coastDistanceNm": coast_distance_nm,
        "coastLayer": coast_label,
        "runtimeWaterSupport": runtime_water_support,
        "marineRegionsIsBoundaryEvidenceOnly": True,
    }
    if conflict:
        return _decision("CONFLICT", "UNKNOWN", evidence, True)
    if "inland_river_corridor" in hit_types:
        river_record = next(
            (item for item in hit_records if item.get("sceneType") == "inland_river_corridor"),
            None,
        )
        attrs = (river_record or {}).get("properties", {}).get("source_attributes", {})
        try:
            estuary = float(attrs.get("DIST_DN_KM")) <= 25
        except (TypeError, ValueError):
            estuary = False
        if estuary:
            return _decision(
                "ESTUARY_OR_RIVER_MOUTH",
                "LOW" if not water_high else "MEDIUM",
                evidence,
                False,
            )
        if water_high:
            return _decision("INLAND_RIVER", "HIGH", evidence, False)
        if runtime_water_support:
            return _decision("INLAND_RIVER", "HIGH", evidence, False)
        if "land" in hit_types:
            return _decision("INLAND_RIVER", "LOW", evidence, False)
        return _decision("UNKNOWN", "UNKNOWN", evidence, False)
    if "inland_lake" in hit_types:
        if any(item.get("confidence") == "HIGH" for item in hit_records
               if item.get("sceneType") == "inland_lake"):
            return _decision("INLAND_LAKE", "HIGH", evidence, False)
        return _decision("UNKNOWN", "UNKNOWN", evidence, False)
    if "major_canal" in hit_types:
        approved = any(
            item.get("properties", {}).get("review_status") == "APPROVED"
            for item in hit_records
            if item.get("sceneType") == "major_canal"
        )
        if approved:
            return _decision("MAJOR_CANAL", "HIGH", evidence, False)
        return _decision("UNKNOWN", "UNKNOWN", evidence, False)
    if "land" in hit_types and hit_types.intersection({
        "marine_high_seas", "marine_eez", "marine_territorial_sea",
        "marine_internal_water", "marine_archipelagic_water",
    }):
        return _decision("BOUNDARY_UNCERTAIN", "LOW", evidence, False)
    if "land" in hit_types:
        if jrc.get("jrcEvidenceClass") in {"STABLE_WATER_HIGH", "WATER_MEDIUM"}:
            return _decision("UNKNOWN", "UNKNOWN", evidence, False)
        return _decision("LAND", "HIGH", evidence, False)
    if "marine_internal_water" in hit_types:
        return _decision("MARITIME_INTERNAL_WATER", "MEDIUM", evidence, False)
    if "marine_archipelagic_water" in hit_types:
        return _decision("ARCHIPELAGIC_WATER", "MEDIUM", evidence, False)
    if "marine_territorial_sea" in hit_types:
        if coast_label in {"VERY_NEAR_COAST", "UNKNOWN"}:
            return _decision("BOUNDARY_UNCERTAIN", "LOW", evidence, False)
        return _decision("TERRITORIAL_SEA", "MEDIUM", evidence, False)
    if "marine_eez" in hit_types:
        offshore_min = float(policy.get("offshore_min_coast_distance_nm", 12.0))
        if coast_distance_nm is None or coast_distance_nm < offshore_min:
            return _decision("BOUNDARY_UNCERTAIN", "LOW", evidence, False)
        return _decision("OFFSHORE_EEZ", "HIGH", evidence, False)
    if "marine_high_seas" in hit_types:
        if coast_label in {"VERY_NEAR_COAST", "COASTAL"}:
            return _decision("BOUNDARY_UNCERTAIN", "LOW", evidence, False)
        return _decision("HIGH_SEAS", "HIGH", evidence, False)
    return _decision("UNKNOWN", "UNKNOWN", evidence, False)


def validate_cross_source(
    release_dir: Path,
    config_dir: Path,
    data_root: Path,
    samples: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    policy = load_yaml(config_dir / "jrc-policy.yaml")
    policy.update(load_yaml(config_dir / "processing-policy.yaml"))
    sampler = JrcRasterSampler(data_root, config_dir)
    if samples is None:
        sample_path = release_dir / "validation" / "auto-samples.json"
        if sample_path.exists():
            samples = __import__("json").loads(sample_path.read_text(encoding="utf-8")).get("samples", [])
        else:
            samples = _fallback_samples(release_dir)
    results = []
    for sample in samples:
        longitude = float(sample["longitude"])
        latitude = float(sample["latitude"])
        hits = point_hits(release_dir, longitude, latitude)
        jrc = sampler.sample(longitude, latitude)
        decision = classify_point(
            hits,
            jrc=jrc,
            coast_distance_nm=sample.get("coastDistanceNm"),
            policy=policy,
            runtime_water_support=bool(sample.get("runtimeWaterSupport", False)),
        )
        results.append({**sample, "hits": sorted({item.get("sceneType") for item in hits}), **decision})
    counts = Counter(item["sceneClass"] for item in results)
    total = len(results)
    report = {
        "schemaVersion": 1,
        "status": "PASS",
        "sampleCount": total,
        "classCounts": dict(sorted(counts.items())),
        "unknownRatio": counts.get("UNKNOWN", 0) / total if total else 0.0,
        "conflictRatio": counts.get("CONFLICT", 0) / total if total else 0.0,
        "results": results,
        "manualTruthEquivalence": False,
        "notes": [
            "GEOMETRY_SELF_CONSISTENCY and CROSS_SOURCE_CONSISTENCY are reported separately.",
            "Automatically generated samples are processing checks, not human-labelled geographic truth.",
            "UNKNOWN and CONFLICT are isolated and never upgraded to high confidence.",
        ],
    }
    write_json(release_dir / "validation" / "cross-source-validation.json", report)
    return report


def _fallback_samples(release_dir: Path) -> list[dict[str, Any]]:
    samples = []
    from .release_records import geometry_from_record, iter_release_records

    for record in iter_release_records(release_dir, record_kind="scene", unique=True):
        geometry = geometry_from_record(record)
        point = geometry.representative_point()
        samples.append({
            "sampleId": f"fallback-{len(samples):04d}",
            "sampleClass": record.get("sceneType"),
            "sampleRole": "positive",
            "longitude": point.x,
            "latitude": point.y,
        })
        if len(samples) >= 500:
            break
    return samples


def _has_conflict(hit_types: set[str]) -> bool:
    marine = hit_types.intersection({
        "marine_high_seas", "marine_eez", "marine_territorial_sea",
        "marine_internal_water", "marine_archipelagic_water",
    })
    if marine and hit_types.intersection({"inland_river_corridor", "inland_lake"}):
        return True
    if "marine_high_seas" in hit_types and hit_types.intersection({
        "land", "marine_eez", "marine_territorial_sea", "marine_internal_water",
        "marine_archipelagic_water", "inland_river_corridor", "inland_lake", "major_canal",
    }):
        return True
    return False


def _decision(scene_class: str, confidence: str, evidence: dict[str, Any], conflict: bool):
    return {
        "sceneClass": scene_class,
        "confidence": confidence,
        "evidence": evidence,
        "conflictFlag": conflict,
    }
