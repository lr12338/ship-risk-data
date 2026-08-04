from __future__ import annotations

import html
import csv
import json
import re
import shutil
import struct
import subprocess
from pathlib import Path
from typing import Any

import fiona
from shapely.geometry import box, shape
from shapely.ops import transform

from .manifest import write_json


REGIONS = [
    ("01-shanghai-yangtze", "Shanghai / Yangtze", (120.5, 30.5, 122.5, 32.0)),
    ("02-rhine-river", "Rhine River", (4.0, 50.0, 8.0, 53.0)),
    ("03-mississippi-river", "Mississippi River", (-92.0, 28.5, -88.0, 31.5)),
    ("04-great-lakes", "Great Lakes", (-93.0, 41.0, -75.0, 50.0)),
    ("05-suez-canal", "Suez Canal", (31.8, 29.7, 32.7, 31.4)),
    ("06-panama-canal", "Panama Canal", (-80.2, 8.7, -79.3, 9.5)),
    ("07-kiel-canal", "Kiel Canal", (8.8, 53.8, 10.3, 54.5)),
    ("08-north-atlantic-high-seas", "North Atlantic High Seas", (-45.0, 25.0, -25.0, 40.0)),
    ("09-offshore-eez", "Offshore EEZ", (-130.0, 35.0, -122.0, 43.0)),
    ("10-indonesia-archipelagic", "Indonesia archipelagic waters", (105.0, -8.0, 120.0, 5.0)),
    ("11-date-line", "International Date Line", (170.0, -20.0, 180.0, 5.0)),
    ("12-coast-boundary", "Coast boundary", (120.5, 30.5, 122.5, 32.0)),
]

LAYER_STYLES = {
    "marine_eez": ("#8ecae6", 0.28, "EEZ"),
    "marine_territorial_sea": ("#219ebc", 0.30, "Territorial sea"),
    "marine_internal_water": ("#90be6d", 0.34, "Internal water"),
    "marine_archipelagic_water": ("#43aa8b", 0.32, "Archipelagic water"),
    "marine_high_seas": ("#577590", 0.20, "High Seas"),
    "land": ("#d9c7a1", 0.78, "Land"),
    "inland_lake": ("#4ea8de", 0.75, "Lake"),
    "inland_river_corridor": ("#277da1", 0.55, "River corridor"),
    "inland_river_centerline": ("none", 1.0, "River centerline"),
    "major_canal": ("#f8961e", 0.70, "Approved canal"),
    "ocean_coastline": ("none", 1.0, "Coastline"),
}


def covered_by_release(
    release_bbox: tuple[float, float, float, float],
    region_bbox: tuple[float, float, float, float],
) -> bool:
    return all(
        (release_bbox[0] <= region_bbox[0], release_bbox[1] <= region_bbox[1],
         release_bbox[2] >= region_bbox[2], release_bbox[3] >= region_bbox[3])
    )


def generate_scene_atlas(release_dir: Path, chrome: str | None = None) -> dict[str, Any]:
    release_dir = release_dir.resolve()
    manifest = json.loads((release_dir / "manifest.json").read_text(encoding="utf-8"))
    report = json.loads((release_dir / "validation-report.json").read_text(encoding="utf-8"))
    canonical = release_dir / "qa" / "maritime-scene-canonical.gpkg"
    samples_path = release_dir / "validation" / "auto-samples-report.json"
    conflicts_path = release_dir / "validation" / "conflict-summary.json"
    if not canonical.exists() or not samples_path.exists() or not conflicts_path.exists():
        raise FileNotFoundError("atlas requires canonical, auto-sample, and conflict QA files")

    first_bbox = report["datasetStatuses"][0].get("bbox") if report.get("datasetStatuses") else None
    release_bbox = tuple(first_bbox) if first_bbox else (-180.0, -90.0, 180.0, 90.0)
    samples = json.loads(samples_path.read_text(encoding="utf-8")).get("results", [])
    conflicts = json.loads(conflicts_path.read_text(encoding="utf-8"))
    output_dir = release_dir / "qa" / "scene-atlas"
    images_dir = output_dir / "images"
    images_dir.mkdir(parents=True, exist_ok=True)
    browser = chrome or _find_chrome()
    _write_qgis_review(release_dir / "qa", canonical, samples)
    results = []
    for key, title, bbox in REGIONS:
        if not covered_by_release(release_bbox, bbox):
            results.append({"key": key, "title": title, "status": "NOT_COVERED", "bbox": bbox})
            continue
        region = _render_region(
            canonical, samples, conflicts, title, bbox,
            manifest["releaseVersion"], manifest["snapshotFingerprint"], report,
        )
        page = output_dir / f".{key}.html"
        page.write_text(region["html"], encoding="utf-8")
        image_path = images_dir / f"{key}.png"
        completed = subprocess.run(
            [browser, "--headless", "--disable-gpu", "--hide-scrollbars", "--no-sandbox",
             f"--screenshot={image_path}", "--window-size=1400,900", page.as_uri()],
            capture_output=True, text=True, check=False,
        )
        page.unlink(missing_ok=True)
        if completed.returncode != 0:
            raise RuntimeError(f"Chrome atlas render failed: {completed.stderr.strip()}")
        width, height = _png_dimensions(image_path)
        checks = {
            "imageNonEmpty": image_path.stat().st_size > 10_000,
            "imageDimensions": [width, height],
            "layerExtentNonEmpty": region["featureCount"] > 0,
            "legendMatchesLayers": set(region["layers"]) == set(region["legendLayers"]),
            "bboxValid": -180 <= bbox[0] < bbox[2] <= 180 and -90 <= bbox[1] < bbox[3] <= 90,
            "boundarySampleCount": region["boundarySampleCount"],
            "boundarySamplesClassified": "NOT_APPLICABLE" if region["boundarySampleCount"] == 0
            else region["boundaryFailures"] == 0,
            "conflictSampleCount": region["conflictSampleCount"],
            "conflictSamplesClassified": "NOT_APPLICABLE" if region["conflictSampleCount"] == 0
            else region["conflictFailures"] == 0,
            "extremePolygonFillsCanvas": region["maxFeatureAreaRatio"] >= 0.98,
        }
        status = "PASS" if (
            checks["imageNonEmpty"] and checks["layerExtentNonEmpty"]
            and checks["legendMatchesLayers"] and checks["bboxValid"]
            and checks["boundarySamplesClassified"] in {True, "NOT_APPLICABLE"}
            and checks["conflictSamplesClassified"] in {True, "NOT_APPLICABLE"}
            and not checks["extremePolygonFillsCanvas"]
        ) else "REVIEW"
        results.append({
            "key": key, "title": title, "status": status, "bbox": bbox,
            "image": f"images/{key}.png", "layers": region["layers"],
            "sources": region["sources"], "checks": checks,
        })

    summary = {
        "schemaVersion": 1,
        "release": manifest["releaseVersion"],
        "releaseStatus": report.get("releaseStatus", report.get("status")),
        "fingerprint": manifest["snapshotFingerprint"],
        "releaseBbox": release_bbox,
        "jrc": {"coverage": "NO_GLOBAL_COVERAGE", "role": "future regional overlay only"},
        "conflictCount": conflicts.get("conflictCount", 0),
        "regions": results,
        "overallStatus": "PASS" if all(item["status"] in {"PASS", "NOT_COVERED"} for item in results) else "REVIEW",
    }
    write_json(output_dir / "summary.json", summary)
    (output_dir / "index.html").write_text(_index_html(summary), encoding="utf-8")
    return summary


def _write_qgis_review(qa_dir: Path, canonical: Path, samples: list[dict[str, Any]]) -> None:
    target = qa_dir / "global-base-review.gpkg"
    target.unlink(missing_ok=True)
    for layer_name in fiona.listlayers(canonical):
        selected = []
        seen = set()
        with fiona.open(canonical, layer=layer_name) as source:
            schema = source.schema.copy()
            crs = source.crs
            for _, _, bbox in REGIONS[:8]:
                for feature in source.filter(bbox=bbox):
                    key = str(feature.get("id")) + str(feature["properties"].get("source_id"))
                    if key in seen:
                        continue
                    seen.add(key)
                    selected.append(feature)
                    if len(selected) >= 200:
                        break
                if len(selected) >= 200:
                    break
        if not selected:
            continue
        with fiona.open(target, "w", driver="GPKG", layer=layer_name,
                        schema=schema, crs=crs) as sink:
            for feature in selected:
                sink.write(feature)
    points = qa_dir / "review-points.csv"
    with points.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["sampleId", "sampleClass", "sceneClass", "confidence", "longitude", "latitude"])
        for sample in samples:
            writer.writerow([sample.get("sampleId"), sample.get("sampleClass"),
                             sample.get("sceneClass"), sample.get("confidence"),
                             sample.get("longitude"), sample.get("latitude")])
    (qa_dir / "QGIS-REVIEW.md").write_text(
        "# QGIS optional review\n\n"
        "Open `global-base-review.gpkg` and `review-points.csv`. Review at most the first "
        "eight atlas regions. This optional visual check is not legal or navigational validation "
        "and does not replace automated gates.\n",
        encoding="utf-8",
    )


def _render_region(
    canonical: Path, samples: list[dict[str, Any]], conflicts: dict[str, Any], title: str,
    bbox: tuple[float, float, float, float], release: str, fingerprint: str,
    report: dict[str, Any],
) -> dict[str, Any]:
    width, height = 1000.0, 760.0
    clip = box(*bbox)
    svg_parts = []
    layers = []
    sources = set()
    feature_count = 0
    max_area_ratio = 0.0
    available = set(fiona.listlayers(canonical))
    for layer, (fill, opacity, _) in LAYER_STYLES.items():
        if layer not in available:
            continue
        fragments = []
        with fiona.open(canonical, layer=layer) as collection:
            for feature in collection.filter(bbox=bbox):
                geometry = shape(feature["geometry"])
                if geometry.is_empty or not geometry.intersects(clip):
                    continue
                geometry = geometry.intersection(clip).simplify(0.0004, preserve_topology=True)
                if geometry.is_empty:
                    continue
                feature_count += 1
                sources.add(str(feature["properties"].get("source_name", "UNKNOWN")))
                max_area_ratio = max(max_area_ratio, geometry.area / max(clip.area, 1e-12))
                projected = transform(lambda x, y, z=None: (
                    20.0 + (x - bbox[0]) / (bbox[2] - bbox[0]) * (width - 40.0),
                    20.0 + (bbox[3] - y) / (bbox[3] - bbox[1]) * (height - 40.0),
                ), geometry)
                fragment = projected.svg(scale_factor=1.0)
                if fill == "none":
                    color = "#172554" if layer == "ocean_coastline" else "#075985"
                    fragment = re.sub(r'stroke="[^"]*"', f'stroke="{color}"', fragment)
                    fragment = re.sub(r'stroke-width="[^"]*"', 'stroke-width="1.4"', fragment)
                else:
                    fragment = re.sub(r'fill="[^"]*"', f'fill="{fill}"', fragment)
                fragments.append(fragment)
        if fragments:
            layers.append(layer)
            svg_parts.append(f'<g opacity="{opacity}">{"".join(fragments)}</g>')

    sample_marks = []
    boundary_failures = 0
    boundary_sample_count = 0
    conflict_failures = 0
    conflict_sample_count = 0
    for sample in samples:
        lon, lat = sample.get("longitude"), sample.get("latitude")
        if lon is None or lat is None or not clip.covers(shape({"type": "Point", "coordinates": [lon, lat]})):
            continue
        scene = sample.get("sceneClass")
        if sample.get("sampleRole") == "boundary" and sample.get("sampleClass") == "TERRITORIAL":
            boundary_sample_count += 1
            if scene != "BOUNDARY_UNCERTAIN":
                boundary_failures += 1
        if sample.get("conflictFlag"):
            conflict_sample_count += 1
            if scene not in {"CONFLICT", "SCENE_CONFLICT"}:
                conflict_failures += 1
        x = 20.0 + (lon - bbox[0]) / (bbox[2] - bbox[0]) * (width - 40.0)
        y = 20.0 + (bbox[3] - lat) / (bbox[3] - bbox[1]) * (height - 40.0)
        stable = bool(sample.get("evidence", {}).get("jrc", {}).get("jrcStableWater"))
        color = "#16a34a" if stable else ("#dc2626" if scene == "CONFLICT" else "#7c3aed")
        radius = 4.0 if scene in {"BOUNDARY_UNCERTAIN", "CONFLICT"} else 2.0
        sample_marks.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{radius}" fill="{color}" opacity="0.72"/>')
    if sample_marks:
        layers.append("auto_samples_and_jrc_evidence")
        svg_parts.append(f'<g>{"".join(sample_marks)}</g>')

    legend_layers = list(layers)
    legend = "".join(
        f'<li><span class="swatch" style="background:{LAYER_STYLES.get(layer, ("#7c3aed",))[0]}"></span>{html.escape(LAYER_STYLES.get(layer, (None, None, "Auto samples / JRC stable water"))[2])}</li>'
        for layer in legend_layers
    )
    sources_text = ", ".join(sorted(sources))
    bbox_text = ", ".join(f"{value:.4f}" for value in bbox)
    status = report.get("releaseStatus", report.get("status", "UNKNOWN"))
    page = f"""<!doctype html><html><head><meta charset="utf-8"><style>
*{{box-sizing:border-box}} body{{margin:0;width:1400px;height:900px;font-family:Arial,sans-serif;color:#172033;background:#f5f7fa}}
.layout{{display:grid;grid-template-columns:1030px 370px;height:900px}} .map{{padding:14px}}
svg{{width:1000px;height:760px;background:#eef7fb;border:1px solid #94a3b8}}
.meta{{padding:24px 22px;background:#fff;border-left:1px solid #cbd5e1}} h1{{font-size:22px;margin:0 0 10px}} h2{{font-size:14px;margin:18px 0 8px}}
p,li{{font-size:12px;line-height:1.45}} ul{{list-style:none;padding:0;margin:0}} .swatch{{display:inline-block;width:14px;height:10px;margin-right:8px;border:1px solid #64748b}}
.axis{{font-size:12px;color:#334155;margin:8px 0 0}} .status{{font-weight:bold;color:#166534}}
</style></head><body><div class="layout"><section class="map"><svg viewBox="0 0 1000 760" role="img" aria-label="{html.escape(title)} maritime scene layers">{"".join(svg_parts)}</svg><p class="axis">WGS84 extent: {bbox_text}</p></section><aside class="meta"><h1>{html.escape(title)}</h1><p><b>Release:</b> {html.escape(release)}<br><b>Fingerprint:</b> {fingerprint[:12]}<br><b>Status:</b> <span class="status">{html.escape(str(status))}</span></p><h2>Layers</h2><ul>{legend}</ul><h2>Scene / confidence evidence</h2><p>Auto sample colors: purple = sample; red = conflict. Labels, boundary state and confidence remain in the source QA report.</p><h2>Sources</h2><p>{html.escape(sources_text)}</p><h2>JRC coverage</h2><p>No global JRC is included. The Shanghai Release remains a separate regional overlay.</p></aside></div></body></html>"""
    return {
        "html": page, "layers": layers, "legendLayers": legend_layers,
        "sources": sorted(sources), "featureCount": feature_count,
        "boundarySampleCount": boundary_sample_count, "boundaryFailures": boundary_failures,
        "conflictSampleCount": conflict_sample_count, "conflictFailures": conflict_failures,
        "maxFeatureAreaRatio": max_area_ratio,
    }


def _find_chrome() -> str:
    candidates = [
        shutil.which("google-chrome"), shutil.which("chromium"),
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return str(candidate)
    raise RuntimeError("Chrome/Chromium is required to render atlas PNG files")


def _png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if header[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError(f"invalid PNG output: {path}")
    return struct.unpack(">II", header[16:24])


def _index_html(summary: dict[str, Any]) -> str:
    sections = []
    for region in summary["regions"]:
        if region["status"] == "NOT_COVERED":
            body = "<p class='not-covered'>NOT_COVERED — this Release contains no data for this extent.</p>"
        else:
            body = f'<img src="{html.escape(region["image"])}" alt="{html.escape(region["title"])} QA map">'
        sections.append(f'<section><h2>{html.escape(region["title"])}</h2>{body}</section>')
    return f"""<!doctype html><html><head><meta charset="utf-8"><title>Maritime Scene QA Atlas</title><style>
body{{font-family:Arial,sans-serif;max-width:1440px;margin:24px auto;padding:0 18px;color:#172033}} img{{max-width:100%;border:1px solid #cbd5e1}} section{{margin:28px 0}} .not-covered{{padding:18px;background:#fff7ed;border-left:4px solid #f97316}} code{{word-break:break-all}}
</style></head><body><h1>Maritime Scene offline QA atlas</h1><p>Release: <b>{html.escape(summary["release"])}</b><br>Fingerprint: <code>{summary["fingerprint"]}</code><br>Status: <b>{summary["overallStatus"]}</b></p>{"".join(sections)}</body></html>"""
