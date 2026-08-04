from __future__ import annotations

import re
import shutil
from pathlib import Path
from typing import Any

from .config import config_hash, load_yaml
from .manifest import sha256_file, utc_now, write_json
from .tiling import jrc_tile_id, jrc_tile_origins


JRC_MANIFEST_NAME = "gsw1_5_2024.json"
JRC_INDEX_NAME = "gsw1_5_2024-index.json"
JRC_TILE_PATTERN = re.compile(
    r"^(?P<product>[a-z_]+)_(?P<tile>\d+[EW]_\d+[NS])_v1_5_2024\.tif$"
)


def load_policy(config_dir: Path) -> dict[str, Any]:
    return load_yaml(config_dir / "jrc-policy.yaml")


def _products(policy: dict[str, Any]) -> dict[str, dict[str, Any]]:
    configured = policy.get("products", {})
    requested = policy.get("download", {}).get("products_for_scene_support", [])
    return {
        name: dict(configured[name])
        for name in requested
        if name in configured
    }


def _tile_url(policy: dict[str, Any], product: str, left_lon: int, top_lat: int) -> str:
    spec = policy["products"][product]
    return (
        f"{policy['download_base_url'].rstrip('/')}/{spec['path']}_"
        f"{jrc_tile_id(left_lon, top_lat)}_v1_5_2024.tif"
    )


def _tile_target(
    data_root: Path, product: str, left_lon: int, top_lat: int
) -> Path:
    tile = jrc_tile_id(left_lon, top_lat)
    return data_root / "raw" / "jrc" / "GSW1_5" / product / (
        f"{product}_{tile}_v1_5_2024.tif"
    )


def _head(url: str) -> dict[str, Any]:
    try:
        import requests

        response = requests.head(url, allow_redirects=True, timeout=20)
        if response.status_code >= 400:
            return {"status": response.status_code}
        return {
            "status": response.status_code,
            "contentLength": int(response.headers.get("Content-Length", 0) or 0),
            "etag": response.headers.get("ETag"),
            "lastModified": response.headers.get("Last-Modified"),
            "contentType": response.headers.get("Content-Type"),
        }
    except Exception as exc:
        return {"status": "HEAD_FAILED", "error": f"{type(exc).__name__}: {exc}"}


def plan(
    data_root: Path,
    config_dir: Path,
    bbox: tuple[float, float, float, float] | None = None,
    mode: str | None = None,
) -> dict[str, Any]:
    policy = load_policy(config_dir)
    download_policy = policy.get("download", {})
    selected_mode = str(mode or download_policy.get("default_mode", "TARGETED")).upper()
    if selected_mode not in {"DISABLED", "TARGETED", "REGIONAL", "GLOBAL_IF_SAFE"}:
        raise ValueError("JRC mode must be DISABLED, TARGETED, REGIONAL, or GLOBAL_IF_SAFE")
    effective_bbox = bbox or (120.5, 30.5, 122.5, 32.0)
    if selected_mode == "DISABLED":
        origins: list[tuple[int, int]] = []
    elif selected_mode == "GLOBAL_IF_SAFE":
        origins = [(lon, lat) for lat in range(-80, 91, 10) for lon in range(-180, 180, 10)]
    else:
        origins = jrc_tile_origins(effective_bbox, int(policy.get("tile_size_degrees", 10)))
    product_specs = _products(policy)
    sample_heads: dict[str, dict[str, Any]] = {}
    if origins:
        sample_left, sample_top = origins[0]
        for product in product_specs:
            sample_heads[product] = _head(_tile_url(policy, product, sample_left, sample_top))
    default_size = int(download_policy.get("default_product_size_bytes", 100_000_000))
    products = []
    total_download_bytes = 0
    for product in sorted(product_specs):
        head = sample_heads.get(product, {})
        size = int(head.get("contentLength") or default_size)
        total_download_bytes += size * len(origins)
        products.append({
            "product": product,
            "path": product_specs[product]["path"],
            "singleTileSizeBytes": size,
            "head": head,
        })
    usage_path = data_root if data_root.exists() else data_root.parent
    usage = shutil.disk_usage(usage_path)
    margin = int(usage.free * float(download_policy.get("safety_margin_ratio", 0.2)))
    uncompressed = int(
        total_download_bytes
        * float(download_policy.get("estimated_uncompressed_ratio", 1.6))
    )
    required = total_download_bytes + uncompressed
    safe = required + margin <= usage.free
    recommended = "TARGETED" if selected_mode == "TARGETED" else (
        "GLOBAL_IF_SAFE" if safe and selected_mode == "GLOBAL_IF_SAFE" else "REGIONAL"
    )
    tile_items = [
        {
            "tileId": jrc_tile_id(left_lon, top_lat),
            "leftLon": left_lon,
            "topLat": top_lat,
            "downloadUrls": {
                product: _tile_url(policy, product, left_lon, top_lat)
                for product in sorted(product_specs)
            },
        }
        for left_lon, top_lat in origins
    ]
    return {
        "schemaVersion": 1,
        "sourceName": policy["source_name"],
        "productName": policy["product_name"],
        "sourceVersion": policy["source_version"],
        "timeRange": policy["time_range"],
        "crs": policy["crs"],
        "tileSizeDegrees": policy["tile_size_degrees"],
        "mode": selected_mode,
        "bbox": list(effective_bbox),
        "tileCount": len(origins),
        "expectedFileCount": len(origins) * len(product_specs),
        "products": products,
        "tiles": tile_items,
        "estimatedDownloadBytes": total_download_bytes,
        "estimatedUncompressedBytes": uncompressed,
        "estimatedProcessingSeconds": len(origins)
        * int(download_policy.get("estimated_seconds_per_tile", 45)),
        "diskAvailableBytes": usage.free,
        "safeMarginBytes": margin,
        "safe": safe,
        "recommendedDownloadMode": recommended,
        "currentIndex": str(data_root / JRC_INDEX_NAME),
        "policySha256": config_hash(config_dir / "jrc-policy.yaml"),
        "officialPage": policy["official_page"],
    }


def acquire(
    data_root: Path,
    config_dir: Path,
    bbox: tuple[float, float, float, float] | None = None,
    mode: str | None = None,
    retries: int = 3,
) -> dict[str, Any]:
    import requests

    planned = plan(data_root, config_dir, bbox=bbox, mode=mode)
    if planned["mode"] == "DISABLED":
        return {"status": "DISABLED", "plan": planned, "files": []}
    if planned["mode"] == "GLOBAL_IF_SAFE" and not planned["safe"]:
        return {"status": "NOT_SAFE", "plan": planned, "files": []}
    policy = load_policy(config_dir)
    manifest_path = data_root / "jrc-manifests" / JRC_MANIFEST_NAME
    old_manifest = _read_json(manifest_path) or {}
    entries = {
        (item.get("product"), item.get("tileId")): item
        for item in old_manifest.get("files", [])
    }
    results = []
    session = requests.Session()
    for tile in planned["tiles"]:
        left_lon = int(tile["leftLon"])
        top_lat = int(tile["topLat"])
        for product in sorted(_products(policy)):
            url = tile["downloadUrls"][product]
            target = _tile_target(data_root, product, left_lon, top_lat)
            target.parent.mkdir(parents=True, exist_ok=True)
            key = (product, tile["tileId"])
            if target.exists() and target.stat().st_size > 0:
                item = dict(entries.get(key, {}))
                item.update({
                    "product": product,
                    "tileId": tile["tileId"],
                    "url": url,
                    "path": target.relative_to(data_root).as_posix(),
                    "size": target.stat().st_size,
                    "sha256": sha256_file(target),
                    "status": "EXISTING",
                })
                entries[key] = item
                results.append(item)
                continue
            part = target.with_name(target.name + ".part")
            last_error: str | None = None
            for attempt in range(max(1, retries)):
                try:
                    offset = part.stat().st_size if part.exists() else 0
                    headers = {"Range": f"bytes={offset}-"} if offset else {}
                    with session.get(url, headers=headers, stream=True, timeout=(20, 120)) as response:
                        response.raise_for_status()
                        append = offset > 0 and response.status_code == 206
                        if offset and not append:
                            part.unlink()
                            offset = 0
                        mode_flag = "ab" if append else "wb"
                        with part.open(mode_flag) as handle:
                            for chunk in response.iter_content(chunk_size=1024 * 1024):
                                if chunk:
                                    handle.write(chunk)
                    part.replace(target)
                    item = {
                        "product": product,
                        "tileId": tile["tileId"],
                        "url": url,
                        "path": target.relative_to(data_root).as_posix(),
                        "size": target.stat().st_size,
                        "sha256": sha256_file(target),
                        "etag": response.headers.get("ETag"),
                        "lastModified": response.headers.get("Last-Modified"),
                        "status": "DOWNLOADED",
                    }
                    entries[key] = item
                    results.append(item)
                    break
                except Exception as exc:
                    last_error = f"{type(exc).__name__}: {exc}"
                    if attempt + 1 == max(1, retries):
                        results.append({
                            "product": product,
                            "tileId": tile["tileId"],
                            "url": url,
                            "status": "FAILED",
                            "error": last_error,
                        })
            if last_error and not any(
                item.get("product") == product
                and item.get("tileId") == tile["tileId"]
                and item.get("status") in {"DOWNLOADED", "EXISTING"}
                for item in results
            ):
                continue
    manifest = {
        "schemaVersion": 1,
        "sourceName": policy["source_name"],
        "productName": policy["product_name"],
        "sourceVersion": policy["source_version"],
        "timeRange": policy["time_range"],
        "crs": policy["crs"],
        "nodata": policy["nodata"],
        "generatedAt": utc_now(),
        "policySha256": config_hash(config_dir / "jrc-policy.yaml"),
        "files": [entries[key] for key in sorted(entries)],
    }
    write_json(manifest_path, manifest)
    return {
        "status": "OK" if not any(item.get("status") == "FAILED" for item in results) else "FAILED",
        "plan": planned,
        "files": results,
        "manifest": str(manifest_path),
    }


def verify(data_root: Path, config_dir: Path, all_files: bool = False) -> dict[str, Any]:
    policy = load_policy(config_dir)
    manifest_path = data_root / "jrc-manifests" / JRC_MANIFEST_NAME
    manifest = _read_json(manifest_path)
    if manifest is None:
        return {
            "status": "NO_COVERAGE",
            "sourceVersion": policy["source_version"],
            "files": [],
        }
    results = []
    for item in manifest.get("files", []):
        path = data_root / item["path"]
        if not path.exists():
            results.append({**item, "status": "MISSING"})
            continue
        actual = sha256_file(path)
        status = "OK" if actual == item.get("sha256") else "INVALID"
        results.append({**item, "status": status, "actualSha256": actual})
    if all_files:
        known = {(data_root / item["path"]).resolve() for item in manifest.get("files", [])}
        for path in sorted((data_root / "raw" / "jrc").glob("GSW1_5/**/*.tif")):
            if path.resolve() not in known:
                results.append({
                    "path": path.relative_to(data_root).as_posix(),
                    "status": "UNMANIFESTED",
                })
    failed = any(item["status"] in {"MISSING", "INVALID", "UNMANIFESTED"} for item in results)
    return {
        "status": "FAILED" if failed else "OK",
        "sourceVersion": manifest.get("sourceVersion", policy["source_version"]),
        "manifest": str(manifest_path),
        "fileCount": len(results),
        "files": results,
    }


def build_index(data_root: Path, config_dir: Path) -> dict[str, Any]:
    policy = load_policy(config_dir)
    files = []
    for path in sorted((data_root / "raw" / "jrc").glob("GSW1_5/**/*.tif")):
        match = JRC_TILE_PATTERN.match(path.name)
        if not match:
            continue
        item: dict[str, Any] = {
            "product": match.group("product"),
            "tileId": match.group("tile"),
            "path": path.relative_to(data_root).as_posix(),
            "sha256": sha256_file(path),
            "size": path.stat().st_size,
            "crs": policy["crs"],
            "nodata": policy["nodata"],
        }
        try:
            import rasterio

            with rasterio.open(path) as source:
                item.update({
                    "crs": source.crs.to_string() if source.crs else policy["crs"],
                    "bounds": list(source.bounds),
                    "width": source.width,
                    "height": source.height,
                    "dtype": source.dtypes[0],
                    "nodata": source.nodata if source.nodata is not None else policy["nodata"],
                    "transform": list(source.transform)[:6],
                })
        except ImportError as exc:
            raise RuntimeError("rasterio is required for jrc-build-index and jrc-sample") from exc
        files.append(item)
    index = {
        "schemaVersion": 1,
        "sourceVersion": policy["source_version"],
        "tileSizeDegrees": policy["tile_size_degrees"],
        "generatedAt": utc_now(),
        "files": files,
    }
    path = data_root / JRC_INDEX_NAME
    write_json(path, index)
    return {"status": "OK", "index": str(path), "fileCount": len(files), "files": files}


class JrcRasterSampler:
    def __init__(self, data_root: Path, config_dir: Path):
        self.data_root = data_root
        self.config_dir = config_dir
        self.policy = load_policy(config_dir)
        self.index = _read_json(data_root / JRC_INDEX_NAME) or {"files": []}
        self._by_key = {
            (item["product"], item["tileId"]): data_root / item["path"]
            for item in self.index.get("files", [])
        }
        self._sources: dict[Path, Any] = {}

    def sample(self, longitude: float, latitude: float) -> dict[str, Any]:
        from .tiling import jrc_tile_origins

        origins = jrc_tile_origins((longitude, latitude, longitude + 1e-9, latitude + 1e-9), 10)
        if not origins:
            return self._no_coverage(longitude, latitude)
        left_lon, top_lat = origins[0]
        tile = jrc_tile_id(left_lon, top_lat)
        values = {
            product: self._read_value(product, tile, longitude, latitude)
            for product in _products(self.policy)
        }
        available = any(value is not None for value in values.values())
        occurrence = values.get("occurrence")
        seasonality = values.get("seasonality")
        recurrence = values.get("recurrence")
        thresholds = self.policy.get("thresholds", {})
        stable = (
            occurrence is not None
            and occurrence >= float(thresholds.get("stable_occurrence_percent", 70))
            and (
                (seasonality is not None and seasonality >= float(thresholds.get("stable_seasonality_months", 9)))
                or (recurrence is not None and recurrence >= float(thresholds.get("stable_recurrence_percent", 70)))
            )
        )
        medium = any([
            occurrence is not None and occurrence >= float(thresholds.get("medium_occurrence_percent", 30)),
            seasonality is not None and seasonality >= float(thresholds.get("medium_seasonality_months", 3)),
            recurrence is not None and recurrence >= float(thresholds.get("medium_recurrence_percent", 30)),
        ])
        any_water = any(value is not None and value > 0 for value in values.values())
        evidence_class = (
            "STABLE_WATER_HIGH" if stable else
            "WATER_MEDIUM" if medium else
            "SEASONAL_OR_UNCERTAIN" if any_water else
            "NO_WATER_EVIDENCE" if available else
            "NO_COVERAGE"
        )
        return {
            "jrcCoverageAvailable": available,
            "jrcWaterOccurrence": occurrence,
            "jrcSeasonality": seasonality,
            "jrcRecurrence": recurrence,
            "jrcStableWater": stable,
            "jrcEvidenceClass": evidence_class,
            "jrcSourceVersion": self.policy["source_version"],
            "jrcTileId": tile,
            "longitude": longitude,
            "latitude": latitude,
        }

    def _no_coverage(self, longitude: float, latitude: float) -> dict[str, Any]:
        return {
            "jrcCoverageAvailable": False,
            "jrcWaterOccurrence": None,
            "jrcSeasonality": None,
            "jrcRecurrence": None,
            "jrcStableWater": False,
            "jrcEvidenceClass": "NO_COVERAGE",
            "jrcSourceVersion": self.policy["source_version"],
            "jrcTileId": None,
            "longitude": longitude,
            "latitude": latitude,
        }

    def _read_value(self, product: str, tile: str, longitude: float, latitude: float):
        path = self._by_key.get((product, tile))
        if path is None or not path.exists():
            return None
        try:
            import rasterio

            source = self._sources.get(path)
            if source is None:
                source = rasterio.open(path)
                self._sources[path] = source
            row, col = source.index(longitude, latitude)
            if row < 0 or col < 0 or row >= source.height or col >= source.width:
                return None
            value = source.read(1, window=((row, row + 1), (col, col + 1)), masked=True)[0, 0]
            if getattr(value, "mask", False):
                return None
            value = float(value)
            if value == float(self.policy.get("nodata", 255)):
                return None
            return value
        except ImportError as exc:
            raise RuntimeError("rasterio is required for JRC sampling") from exc

    def close(self) -> None:
        for source in self._sources.values():
            source.close()
        self._sources.clear()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()


def sample_point(
    data_root: Path, config_dir: Path, longitude: float, latitude: float
) -> dict[str, Any]:
    with JrcRasterSampler(data_root, config_dir) as sampler:
        return sampler.sample(longitude, latitude)


def _read_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    import json

    return json.loads(path.read_text(encoding="utf-8"))
