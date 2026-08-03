from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .auto_samples import generate_auto_samples, validate_auto_samples
from .coast_distance import distance_to_coast
from .conflict_export import export_conflicts
from .cross_source_validation import validate_cross_source
from .manifest import write_json
from .overlap_matrix import calculate_overlap_matrix, write_overlap_outputs
from .topology_validation import validate_topology


def run_automatic_validation(
    release_dir: Path,
    config_dir: Path,
    data_root: Path,
) -> dict[str, Any]:
    topology = validate_topology(release_dir, config_dir)
    overlap = calculate_overlap_matrix(release_dir)
    write_overlap_outputs(release_dir, overlap)
    samples = generate_auto_samples(release_dir, config_dir)
    coast = _attach_coast_distances(release_dir, samples)
    auto_samples = validate_auto_samples(release_dir, config_dir, data_root)
    cross = validate_cross_source(
        release_dir,
        config_dir,
        data_root,
        samples=json.loads(
            (release_dir / "validation" / "auto-samples.json").read_text(encoding="utf-8")
        ).get("samples", []),
    )
    conflicts = export_conflicts(release_dir)
    fatal = bool(topology.get("geometry", {}).get("parseErrorCount"))
    report = {
        "schemaVersion": 1,
        "status": "FAILED" if fatal else ("WARN" if conflicts.get("conflictCount") else "PASS"),
        "releaseStatus": "AUTO_CROSS_VALIDATED" if not fatal else "GEOMETRY_VALIDATED",
        "topology": topology,
        "overlapMatrix": {
            "status": overlap.get("status"),
            "requiredPairCount": overlap.get("requiredPairCount"),
            "conflictPairCount": overlap.get("conflictPairCount"),
        },
        "coastDistance": coast,
        "autoSamples": {
            "status": auto_samples.get("status"),
            "sampleCount": auto_samples.get("sampleCount"),
            "failedCount": auto_samples.get("failedCount"),
        },
        "crossSource": {
            "status": cross.get("status"),
            "sampleCount": cross.get("sampleCount"),
            "unknownRatio": cross.get("unknownRatio"),
            "conflictRatio": cross.get("conflictRatio"),
        },
        "conflicts": {
            "status": conflicts.get("status"),
            "count": conflicts.get("conflictCount"),
            "blocking": False,
        },
        "manualSpotCheck": {"status": "NOT_PERFORMED", "blocking": False},
        "evidencePolicy": {
            "jrcRequiredForHighInlandWater": True,
            "jrcMissingMeansNoWaterSupport": True,
            "marineRegionsIsNotLegalOrNavigationalRuling": True,
            "unknownAndConflictAreNotHardExclusions": True,
        },
    }
    write_json(release_dir / "validation" / "automatic-validation.json", report)
    return report


def _attach_coast_distances(release_dir: Path, payload: dict[str, Any]) -> dict[str, Any]:
    samples = payload.get("samples", [])
    results = []
    for sample in samples[:200]:
        result = distance_to_coast(
            release_dir,
            float(sample["longitude"]),
            float(sample["latitude"]),
        )
        sample["coastDistanceNm"] = result.get("distanceNm")
        sample["coastDistanceAvailable"] = result.get("available")
        results.append(result)
    write_json(release_dir / "validation" / "auto-samples.json", payload)
    distances = sorted(
        float(item["distanceNm"])
        for item in results
        if item.get("available") and item.get("distanceNm") is not None
    )
    report = {
        "schemaVersion": 1,
        "status": "PASS" if results else "UNKNOWN",
        "sampleCount": len(results),
        "availableCount": len(distances),
        "distanceNm": {
            "min": distances[0] if distances else None,
            "p50": distances[(len(distances) - 1) // 2] if distances else None,
            "max": distances[-1] if distances else None,
        },
        "results": results,
        "method": "local azimuthal-equidistant projection plus indexed coastline tiles",
    }
    write_json(release_dir / "validation" / "coast-distance-report.json", report)
    return report
