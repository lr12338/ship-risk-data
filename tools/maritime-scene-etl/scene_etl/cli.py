from __future__ import annotations

import argparse
import importlib.util
import json
import platform
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Any

from . import __version__
from .acquire import ManualDownloadRequired, acquire_dataset, target_path
from .config import dataset_by_id, load_datasets
from .fingerprint import fingerprint_from_manifest, fingerprint_inputs
from .golden_candidates import generate_candidate_report
from .geometry import extract_zip, select_vector_path
from .automatic_validation import run_automatic_validation
from .auto_samples import generate_auto_samples, validate_auto_samples
from .coast_distance import distance_to_coast
from .config import config_hash
from .cross_source_validation import validate_cross_source
from .estimate_build import estimate_build
from .jrc import acquire as acquire_jrc
from .jrc import build_index as build_jrc_index
from .jrc import plan as plan_jrc
from .jrc import sample_point as sample_jrc_point
from .jrc import verify as verify_jrc
from .manifest import canonical_sha256, utc_now, write_json
from .overlap_matrix import calculate_overlap_matrix, write_overlap_outputs
from .pipeline import build_release, normalize_all, normalize_dataset
from .release_status import RELEASE_STATUSES, can_promote
from .validate import validate_release
from .verify import verify_dataset


TOOL_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = TOOL_ROOT.parents[1]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="scene-etl",
        description="Offline maritime scene data acquisition and release ETL",
    )
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT)
    parser.add_argument("--data-root", type=Path, default=None)
    parser.add_argument("--config-dir", type=Path, default=TOOL_ROOT / "config")
    parser.add_argument("--json", action="store_true", help="emit JSON output")
    subparsers = parser.add_subparsers(dest="command", required=True)

    doctor_parser = subparsers.add_parser("doctor", help="inspect local runtime and disk prerequisites")
    doctor_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    list_parser = subparsers.add_parser("list-datasets", help="list registered datasets")
    list_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)

    for name, help_text in (
        ("acquire", "download or report manually gated raw sources"),
        ("verify-raw", "verify raw files, manifests, hashes, and ZIP CRCs"),
        ("inspect", "inspect raw archive members and vector metadata"),
        ("normalize", "normalize selected sources into deterministic records"),
    ):
        command = subparsers.add_parser(name, help=help_text)
        _add_selection_options(command)
        command.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
        if name == "acquire":
            command.add_argument("--retries", type=int, default=3)
        if name == "normalize":
            command.add_argument("--force", action="store_true")
            command.add_argument("--bbox", type=str, default=None, help="minLon,minLat,maxLon,maxLat")

    build_parser_ = subparsers.add_parser("build", help="build a versioned release")
    build_parser_.add_argument("--release", required=True, help="release directory name")
    build_parser_.add_argument("--force", action="store_true")
    build_parser_.add_argument("--bbox", type=str, default=None, help="minLon,minLat,maxLon,maxLat")
    build_parser_.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)

    estimate_parser = subparsers.add_parser("estimate-build", help="estimate a global or partitioned build")
    estimate_parser.add_argument("--scope", choices=["global", "regional", "partition"], required=True)
    estimate_parser.add_argument("--bbox", type=str, default=None)
    estimate_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)

    for name, help_text in (
        ("validate", "validate a release and golden-point gate"),
        ("fingerprint", "show the deterministic snapshot fingerprint inputs"),
        ("report", "print a release validation report"),
    ):
        command = subparsers.add_parser(name, help=help_text)
        command.add_argument("--release", required=True)
        command.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    candidates_parser = subparsers.add_parser(
        "golden-candidates", help="generate deterministic Golden Point candidate coordinates"
    )
    candidates_parser.add_argument("--release", required=True)
    candidates_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)

    jrc_plan_parser = subparsers.add_parser("jrc-plan", help="plan safe targeted or regional JRC downloads")
    jrc_plan_parser.add_argument("--bbox", type=str, default=None)
    jrc_plan_parser.add_argument("--mode", choices=["DISABLED", "TARGETED", "REGIONAL", "GLOBAL_IF_SAFE"], default=None)
    jrc_plan_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    jrc_acquire_parser = subparsers.add_parser("jrc-acquire", help="download targeted JRC 10 degree GeoTIFF tiles")
    jrc_acquire_parser.add_argument("--bbox", type=str, default=None)
    jrc_acquire_parser.add_argument("--mode", choices=["DISABLED", "TARGETED", "REGIONAL", "GLOBAL_IF_SAFE"], default=None)
    jrc_acquire_parser.add_argument("--retries", type=int, default=3)
    jrc_acquire_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    jrc_verify_parser = subparsers.add_parser("jrc-verify", help="verify JRC manifest and SHA-256 files")
    jrc_verify_parser.add_argument("--all", action="store_true")
    jrc_verify_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    jrc_index_parser = subparsers.add_parser("jrc-build-index", help="build a local JRC raster tile index")
    jrc_index_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    jrc_sample_parser = subparsers.add_parser("jrc-sample", help="sample JRC water evidence at a point")
    jrc_sample_parser.add_argument("--lon", type=float, required=True)
    jrc_sample_parser.add_argument("--lat", type=float, required=True)
    jrc_sample_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)

    for name, help_text in (
        ("overlap-matrix", "calculate the deterministic layer overlap matrix"),
        ("generate-auto-samples", "generate automatic cross-source samples"),
        ("validate-auto-samples", "validate automatic cross-source samples"),
        ("validate-cross-source", "validate cross-source scene classifications"),
    ):
        command = subparsers.add_parser(name, help=help_text)
        command.add_argument("--release", required=True)
        if name == "generate-auto-samples":
            command.add_argument("--max-total", type=int, default=None)
        command.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    coast_parser = subparsers.add_parser("coast-distance", help="calculate geodesic distance to indexed coastline")
    coast_parser.add_argument("--release", required=True)
    coast_parser.add_argument("--lon", type=float, required=True)
    coast_parser.add_argument("--lat", type=float, required=True)
    coast_parser.add_argument("--search-radius-nm", type=float, default=60.0)
    coast_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)

    status_parser = subparsers.add_parser("release-status", help="promote an immutable release status")
    status_parser.add_argument("--release", required=True)
    status_parser.add_argument("--status", choices=["SHADOW_READY"], required=True)
    status_parser.add_argument("--policy-version", default="MARITIME_SCENE_SHADOW_V1")
    status_parser.add_argument("--json", action="store_true", dest="json_output", default=argparse.SUPPRESS)
    return parser


def _add_selection_options(parser: argparse.ArgumentParser) -> None:
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--dataset", nargs="+", help="one or more dataset IDs")
    group.add_argument("--all", action="store_true", help="select every enabled dataset")


def _paths(args: argparse.Namespace) -> tuple[Path, Path, Path]:
    repo_root = args.repo_root.resolve()
    data_root = (args.data_root or repo_root / "var" / "maritime-scene-data").resolve()
    config_dir = args.config_dir.resolve()
    return repo_root, data_root, config_dir


def _selected(args: argparse.Namespace, config_dir: Path) -> list[dict[str, Any]]:
    datasets = load_datasets(config_dir)
    if args.all:
        return [item for item in datasets if item["enabled"]]
    return [dataset_by_id(config_dir, item) for item in args.dataset]


def _parse_bbox(value: str | None) -> tuple[float, float, float, float] | None:
    if value is None:
        return None
    try:
        numbers = tuple(float(item.strip()) for item in value.split(","))
    except ValueError as exc:
        raise ValueError("bbox must be minLon,minLat,maxLon,maxLat") from exc
    if len(numbers) != 4:
        raise ValueError("bbox must be minLon,minLat,maxLon,maxLat")
    min_lon, min_lat, max_lon, max_lat = numbers
    if not (-180 <= min_lon < max_lon <= 180 and -90 <= min_lat < max_lat <= 90):
        raise ValueError("bbox must be ordered and within WGS84 bounds")
    return numbers  # type: ignore[return-value]


def _emit(value: Any, args: argparse.Namespace) -> None:
    as_json = bool(getattr(args, "json", False) or getattr(args, "json_output", False))
    if as_json or not isinstance(value, list):
        print(json.dumps(value, ensure_ascii=False, indent=2, default=str))
        return
    for item in value:
        if isinstance(item, dict):
            print(
                f"{item.get('dataset_id', item.get('datasetId', '')):<32} "
                f"{item.get('status', item.get('download_mode', '')):<24} "
                f"{item.get('product_name', item.get('path', ''))}"
            )
        else:
            print(item)


def _write_command_report(data_root: Path, command: str, value: Any) -> None:
    write_json(
        data_root / "reports" / f"{command}.json",
        {"schemaVersion": 1, "command": command, "generatedAt": utc_now(), "result": value},
    )


def cmd_doctor(args: argparse.Namespace) -> int:
    repo_root, data_root, _ = _paths(args)
    packages = {
        name: bool(importlib.util.find_spec(name))
        for name in ("fiona", "osmium", "psutil", "pyproj", "requests", "shapely", "yaml")
    }
    commands = {
        name: shutil.which(name) or "NOT_FOUND"
        for name in ("gdalinfo", "ogrinfo", "ogr2ogr", "proj", "osmium", "docker", "java", "mvn")
    }
    disk_path = data_root
    while not disk_path.exists() and disk_path != disk_path.parent:
        disk_path = disk_path.parent
    usage = shutil.disk_usage(disk_path)
    result = {
        "etlVersion": __version__,
        "repoRoot": str(repo_root),
        "dataRoot": str(data_root),
        "platform": platform.platform(),
        "machine": platform.machine(),
        "python": sys.version.split()[0],
        "pythonSupported": sys.version_info >= (3, 10) and sys.version_info < (3, 15),
        "packages": packages,
        "commands": commands,
        "disk": {"freeBytes": usage.free, "totalBytes": usage.total},
        "venv": str(TOOL_ROOT / ".venv"),
    }
    _emit(result, args)
    return 0 if result["pythonSupported"] else 1


def cmd_list_datasets(args: argparse.Namespace) -> int:
    _, _, config_dir = _paths(args)
    datasets = load_datasets(config_dir)
    _emit(datasets, args)
    return 0


def cmd_acquire(args: argparse.Namespace) -> int:
    repo_root, data_root, config_dir = _paths(args)
    results = []
    for dataset in _selected(args, config_dir):
        try:
            results.append(acquire_dataset(repo_root, data_root, dataset, retries=args.retries))
        except ManualDownloadRequired as exc:
            results.append({"datasetId": dataset["dataset_id"], "status": "BLOCKED_MANUAL", "message": str(exc)})
        except Exception as exc:
            results.append({"datasetId": dataset["dataset_id"], "status": "FAILED", "error": f"{type(exc).__name__}: {exc}"})
    _write_command_report(data_root, "acquire", results)
    _emit(results, args)
    if any(item.get("status") == "FAILED" for item in results):
        return 1
    return 2 if any(item.get("status") == "BLOCKED_MANUAL" for item in results) else 0


def cmd_verify_raw(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    results = [verify_dataset(data_root, dataset) for dataset in _selected(args, config_dir)]
    _write_command_report(data_root, "verify-raw", results)
    _emit(results, args)
    if any(item.get("status") in {"MISSING", "INVALID"} for item in results):
        return 1
    return 2 if any(item.get("status") == "BLOCKED_MANUAL" for item in results) else 0


def cmd_inspect(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    results = [_inspect_dataset(data_root, dataset) for dataset in _selected(args, config_dir)]
    _write_command_report(data_root, "inspect", results)
    _emit(results, args)
    return 1 if any(item.get("status") in {"MISSING", "INVALID", "FAILED"} for item in results) else 0


def _inspect_dataset(data_root: Path, dataset: dict[str, Any]) -> dict[str, Any]:
    target = target_path(data_root, dataset)
    result: dict[str, Any] = {
        "datasetId": dataset["dataset_id"],
        "path": str(target),
        "status": "OK" if target.exists() else ("BLOCKED_MANUAL" if dataset["manual_download"] else "MISSING"),
    }
    if not target.exists():
        return result
    result["fileSize"] = target.stat().st_size
    if target.suffix.lower() != ".zip":
        result["format"] = dataset["expected_format"]
        if dataset["expected_format"] == "OSM_PBF" and shutil.which("osmium"):
            completed = subprocess.run(
                ["osmium", "fileinfo", "-e", str(target)], capture_output=True, text=True, check=False
            )
            result["osmiumFileInfo"] = completed.stdout[-12000:]
        return result
    with zipfile.ZipFile(target) as archive:
        members = archive.namelist()
        result["zipMembers"] = len(members)
        result["vectorMembers"] = [name for name in members if name.lower().endswith((".shp", ".dbf", ".prj"))]
    staging = extract_zip(target, data_root / "staging" / dataset["dataset_id"])
    shapefiles = sorted(staging.rglob("*.shp"))
    if not shapefiles:
        return result
    try:
        import fiona

        expected_kind = "polygon" if "polygon" in dataset.get("expected_layer", "").lower() else None
        source_path = select_vector_path(shapefiles, expected_kind=expected_kind)
        with fiona.open(source_path) as source:
            result["layer"] = source_path.stem
            result["crs"] = source.crs_wkt or str(source.crs) or "UNKNOWN"
            result["schema"] = dict(source.schema)
            result["featureCount"] = len(source)
            _record_inspected_crs(data_root, dataset, result["crs"])
    except Exception as exc:
        result["status"] = "FAILED"
        result["error"] = f"{type(exc).__name__}: {exc}"
    return result


def _record_inspected_crs(data_root: Path, dataset: dict[str, Any], raw_crs: str) -> None:
    manifest_path = data_root / "raw-manifests" / f"{dataset['dataset_id']}.json"
    if not manifest_path.exists() or not raw_crs or raw_crs == "UNKNOWN":
        return
    try:
        from pyproj import CRS

        source_crs = CRS.from_user_input(raw_crs).to_string()
    except Exception:
        source_crs = raw_crs
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("sourceCrs") != source_crs:
        manifest["sourceCrs"] = source_crs
        write_json(manifest_path, manifest)


def cmd_normalize(args: argparse.Namespace) -> int:
    repo_root, data_root, config_dir = _paths(args)
    bbox = _parse_bbox(args.bbox)
    datasets = _selected(args, config_dir)
    if args.all:
        results = normalize_all(repo_root, data_root, config_dir, datasets, force=args.force, bbox=bbox)
    else:
        results = [
            normalize_dataset(repo_root, data_root, config_dir, dataset, force=args.force, bbox=bbox)
            for dataset in datasets
        ]
    _write_command_report(data_root, "normalize", results)
    _emit(results, args)
    if any(item.get("status") in {"FAILED", "MISSING", "INVALID"} for item in results):
        return 1
    return 2 if any(item.get("status") == "BLOCKED_MANUAL" for item in results) else 0


def cmd_build(args: argparse.Namespace) -> int:
    repo_root, data_root, config_dir = _paths(args)
    result = build_release(
        repo_root,
        data_root,
        config_dir,
        args.release,
        force=args.force,
        bbox=_parse_bbox(args.bbox),
    )
    _emit(result, args)
    status = result.get("validation", {}).get("status")
    return 0 if status in {"PASS", "GEOMETRY_VALIDATED", "AUTO_CROSS_VALIDATED", "SHADOW_READY"} else (2 if status == "PARTIAL_MANUAL_ACTION_REQUIRED" else 1)


def cmd_estimate_build(args: argparse.Namespace) -> int:
    repo_root, data_root, config_dir = _paths(args)
    result = estimate_build(data_root, config_dir, args.scope, _parse_bbox(args.bbox))
    _write_command_report(data_root, "estimate-build", result)
    _emit(result, args)
    return 0


def cmd_jrc_plan(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    result = plan_jrc(data_root, config_dir, _parse_bbox(args.bbox), args.mode)
    _write_command_report(data_root, "jrc-plan", result)
    _emit(result, args)
    return 0


def cmd_jrc_acquire(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    result = acquire_jrc(data_root, config_dir, _parse_bbox(args.bbox), args.mode, args.retries)
    _write_command_report(data_root, "jrc-acquire", result)
    _emit(result, args)
    return 0 if result.get("status") in {"OK", "DISABLED"} else 1


def cmd_jrc_verify(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    result = verify_jrc(data_root, config_dir, args.all)
    _write_command_report(data_root, "jrc-verify", result)
    _emit(result, args)
    return 0 if result.get("status") in {"OK", "NO_COVERAGE"} else 1


def cmd_jrc_build_index(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    result = build_jrc_index(data_root, config_dir)
    _write_command_report(data_root, "jrc-build-index", result)
    _emit(result, args)
    return 0


def cmd_jrc_sample(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    result = sample_jrc_point(data_root, config_dir, args.lon, args.lat)
    _emit(result, args)
    return 0


def _release_dir(args: argparse.Namespace) -> tuple[Path, Path, Path]:
    repo_root, data_root, config_dir = _paths(args)
    release_dir = data_root / "releases" / args.release
    if not release_dir.exists():
        raise FileNotFoundError(f"release not found: {release_dir}")
    return repo_root, data_root, config_dir


def cmd_overlap_matrix(args: argparse.Namespace) -> int:
    _, data_root, _ = _release_dir(args)
    release_dir = data_root / "releases" / args.release
    result = calculate_overlap_matrix(release_dir)
    write_overlap_outputs(release_dir, result)
    _emit(result, args)
    return 0


def cmd_generate_auto_samples(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _release_dir(args)
    release_dir = data_root / "releases" / args.release
    result = generate_auto_samples(release_dir, config_dir, args.max_total)
    _emit(result, args)
    return 0


def cmd_validate_auto_samples(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _release_dir(args)
    release_dir = data_root / "releases" / args.release
    result = validate_auto_samples(release_dir, config_dir, data_root)
    _emit(result, args)
    return 0 if result.get("status") in {"PASS", "WARN"} else 1


def cmd_validate_cross_source(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _release_dir(args)
    release_dir = data_root / "releases" / args.release
    result = validate_cross_source(release_dir, config_dir, data_root)
    _emit(result, args)
    return 0 if result.get("status") in {"PASS", "WARN"} else 1


def cmd_coast_distance(args: argparse.Namespace) -> int:
    _, data_root, _ = _release_dir(args)
    release_dir = data_root / "releases" / args.release
    result = distance_to_coast(release_dir, args.lon, args.lat, args.search_radius_nm)
    _emit(result, args)
    return 0


def cmd_release_status(args: argparse.Namespace) -> int:
    repo_root, data_root, config_dir = _release_dir(args)
    release_dir = data_root / "releases" / args.release
    report_path = release_dir / "validation-report.json"
    report = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {}
    current = str(report.get("releaseStatus", report.get("status", "GEOMETRY_VALIDATED")))
    if not can_promote(current, args.status):
        raise ValueError(f"release status transition is not allowed: {current} -> {args.status}")
    automatic_path = release_dir / "validation" / "automatic-validation.json"
    automatic = json.loads(automatic_path.read_text(encoding="utf-8")) if automatic_path.exists() else {}
    if automatic.get("status") not in {"PASS", "WARN"}:
        raise ValueError("automatic validation must complete before SHADOW_READY")
    report["status"] = args.status
    report["releaseStatus"] = args.status
    report["policyVersion"] = args.policy_version
    report["shadowEnabledByDefault"] = False
    report.pop("snapshotFingerprint", None)
    write_json(report_path, report)
    datasets = [item for item in load_datasets(config_dir) if item["enabled"]]
    from .fingerprint import write_release_manifest

    fingerprint, manifest = write_release_manifest(
        repo_root, config_dir, data_root, release_dir, datasets, report
    )
    report["snapshotFingerprint"] = fingerprint
    write_json(report_path, report)
    fingerprint, manifest = write_release_manifest(
        repo_root, config_dir, data_root, release_dir, datasets, report
    )
    result = {
        "release": args.release,
        "status": args.status,
        "policyVersion": args.policy_version,
        "snapshotFingerprint": fingerprint,
        "manifest": manifest,
    }
    _emit(result, args)
    return 0


def _release_statuses(release_dir: Path) -> list[dict[str, Any]]:
    status_file = release_dir / "source-manifests" / "dataset-status.json"
    if not status_file.exists():
        return []
    return json.loads(status_file.read_text(encoding="utf-8")).get("datasetStatuses", [])


def cmd_validate(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    release_dir = data_root / "releases" / args.release
    automatic_path = release_dir / "validation" / "automatic-validation.json"
    if not automatic_path.exists():
        run_automatic_validation(release_dir, config_dir, data_root)
    result = validate_release(
        release_dir,
        config_dir,
        _release_statuses(release_dir),
        write_report=False,
    )
    manifest_path = release_dir / "manifest.json"
    if manifest_path.exists():
        result["snapshotFingerprint"] = fingerprint_from_manifest(manifest_path)
    _emit(result, args)
    return 0 if result["status"] in {"PASS", "GEOMETRY_VALIDATED", "AUTO_CROSS_VALIDATED", "SHADOW_READY"} else (2 if "MANUAL" in result["status"] else 1)


def cmd_fingerprint(args: argparse.Namespace) -> int:
    repo_root, data_root, config_dir = _paths(args)
    release_dir = data_root / "releases" / args.release
    manifest_path = release_dir / "manifest.json"
    if manifest_path.exists():
        result = {"snapshotFingerprint": fingerprint_from_manifest(manifest_path), "manifest": str(manifest_path)}
    else:
        datasets = [item for item in load_datasets(config_dir) if item["enabled"]]
        inputs = fingerprint_inputs(repo_root, config_dir, data_root, release_dir, datasets)
        result = {"snapshotFingerprint": canonical_sha256(inputs), "fingerprintInputs": inputs}
    _emit(result, args)
    return 0


def cmd_report(args: argparse.Namespace) -> int:
    _, data_root, _ = _paths(args)
    path = data_root / "releases" / args.release / "validation-report.json"
    if not path.exists():
        raise FileNotFoundError(f"validation report not found: {path}")
    result = json.loads(path.read_text(encoding="utf-8"))
    _emit(result, args)
    return 0


def cmd_golden_candidates(args: argparse.Namespace) -> int:
    _, data_root, config_dir = _paths(args)
    release_dir = data_root / "releases" / args.release
    result = generate_candidate_report(release_dir, config_dir)
    _write_command_report(data_root, "golden-candidates", result)
    _emit(result, args)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        commands = {
            "doctor": cmd_doctor,
            "list-datasets": cmd_list_datasets,
            "acquire": cmd_acquire,
            "verify-raw": cmd_verify_raw,
            "inspect": cmd_inspect,
            "normalize": cmd_normalize,
            "build": cmd_build,
            "estimate-build": cmd_estimate_build,
            "validate": cmd_validate,
            "fingerprint": cmd_fingerprint,
            "report": cmd_report,
            "golden-candidates": cmd_golden_candidates,
            "jrc-plan": cmd_jrc_plan,
            "jrc-acquire": cmd_jrc_acquire,
            "jrc-verify": cmd_jrc_verify,
            "jrc-build-index": cmd_jrc_build_index,
            "jrc-sample": cmd_jrc_sample,
            "overlap-matrix": cmd_overlap_matrix,
            "generate-auto-samples": cmd_generate_auto_samples,
            "validate-auto-samples": cmd_validate_auto_samples,
            "validate-cross-source": cmd_validate_cross_source,
            "coast-distance": cmd_coast_distance,
            "release-status": cmd_release_status,
        }
        return commands[args.command](args)
    except ManualDownloadRequired as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except (FileNotFoundError, KeyError, RuntimeError, ValueError, zipfile.BadZipFile) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
