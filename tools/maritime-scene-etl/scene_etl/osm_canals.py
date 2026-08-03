from __future__ import annotations

from pathlib import Path
from typing import Any, Iterator

from .config import load_yaml
from .geometry import metric_buffer
from .records import scene_record


def iter_records(
    repo_root: Path,
    data_root: Path,
    config_dir: Path,
    dataset: dict[str, Any],
    stats: dict[str, Any],
    force: bool = False,
    bbox: tuple[float, float, float, float] | None = None,
) -> Iterator[dict[str, Any]]:
    try:
        import osmium
    except ImportError as exc:
        raise RuntimeError(
            "osmium Python bindings are required for OSM PBF processing; run the ETL venv"
        ) from exc
    raw = data_root / "raw" / dataset["raw_target_path"]
    canal_config = load_yaml(config_dir / "major-canals.yaml")
    wanted = {
        item["canal_id"]: item
        for item in canal_config.get("canals", [])
        if item["canal_id"] in dataset.get("canal_ids", [])
    }
    found: list[dict[str, Any]] = []

    class Handler(osmium.SimpleHandler):
        def way(self, way):  # type: ignore[no-untyped-def]
            tags = {str(key): str(value) for key, value in way.tags}
            names = " ".join(
                tags.get(key, "") for key in ("name", "name:en", "alt_name", "official_name", "ref")
            ).casefold()
            waterway = tags.get("waterway", "").casefold()
            if waterway not in {"canal", "river", "drain"}:
                return
            for canal_id, spec in wanted.items():
                aliases = [spec["canonical_name"], *spec.get("aliases", [])]
                if not any(alias.casefold() in names for alias in aliases):
                    continue
                nodes = []
                for node in way.nodes:
                    if node.location.valid():
                        nodes.append((node.lon, node.lat))
                if len(nodes) < 2:
                    return
                found.append({
                    "canal": spec,
                    "way_id": str(way.id),
                    "tags": tags,
                    "coordinates": nodes,
                })
                return

    handler = Handler()
    handler.apply_file(raw, locations=True)
    stats["candidateCount"] = len(found)
    from shapely.geometry import box

    requested_bbox = box(*bbox) if bbox is not None else None
    for item in sorted(found, key=lambda value: (value["canal"]["canal_id"], value["way_id"])):
        from shapely.geometry import LineString

        spec = item["canal"]
        line = LineString(item["coordinates"])
        if requested_bbox is not None and not line.intersects(requested_bbox):
            stats["requestedBboxRejected"] = stats.get("requestedBboxRejected", 0) + 1
            continue
        expected_bbox = box(*spec["expected_bbox"])
        if not line.intersects(expected_bbox):
            stats["bboxRejected"] = stats.get("bboxRejected", 0) + 1
            continue
        approved = spec.get("review_status") == "APPROVED"
        stats["approvedCount" if approved else "reviewRequiredCount"] = stats.get(
            "approvedCount" if approved else "reviewRequiredCount", 0
        ) + 1
        properties = {
            "canal_id": spec["canal_id"],
            "canonical_name": spec["canonical_name"],
            "review_status": spec.get("review_status"),
            "osm_way_id": item["way_id"],
            "source_attributes": item["tags"],
        }
        if approved:
            yield scene_record(
                "major_canal", dataset["source_name"], dataset["source_version"],
                f"{spec['canal_id']}:{item['way_id']}", line, "VALID_ORIGINAL",
                "OSM_REVIEWED_WHITELIST", "HIGH", properties
            )
        else:
            yield scene_record(
                "major_canal_review_required", dataset["source_name"], dataset["source_version"],
                f"{spec['canal_id']}:{item['way_id']}", metric_buffer(line, float(spec["buffer_meters"])),
                "VALID_ORIGINAL", "REVIEW_REQUIRED", "REVIEW_REQUIRED", properties, record_kind="review"
            )
