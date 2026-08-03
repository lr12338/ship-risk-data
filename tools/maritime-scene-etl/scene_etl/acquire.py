from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .manifest import raw_manifest_path, sha256_file, utc_now, write_json


class ManualDownloadRequired(RuntimeError):
    """Raised when a source explicitly requires a person to download it."""


def target_path(data_root: Path, dataset: dict[str, Any]) -> Path:
    return data_root / "raw" / dataset["raw_target_path"]


def _http_headers(path: Path) -> dict[str, str]:
    """Return headers for a resumable request without leaking credentials."""
    if not path.exists():
        return {}
    return {"Range": f"bytes={path.stat().st_size}-"}


def acquire_dataset(
    repo_root: Path, data_root: Path, dataset: dict[str, Any], retries: int = 3
) -> dict[str, Any]:
    target = target_path(data_root, dataset)
    manifest_file = raw_manifest_path(data_root, dataset["dataset_id"])
    if dataset["manual_download"]:
        if not target.exists():
            raise ManualDownloadRequired(_manual_message(data_root, dataset))
        return verify_existing(repo_root, data_root, dataset, target, manifest_file)

    url = dataset.get("download_url")
    if not url:
        raise ValueError(f"dataset has no download_url: {dataset['dataset_id']}")
    target.parent.mkdir(parents=True, exist_ok=True)
    part = target.with_name(target.name + ".part")

    try:
        import requests
    except ImportError as exc:
        raise RuntimeError(
            "requests is required; run `make -C tools/maritime-scene-etl venv`"
        ) from exc

    last_error: Exception | None = None
    response = None
    for attempt in range(1, retries + 1):
        try:
            headers = _http_headers(part)
            response = requests.get(
                url, headers=headers, stream=True, timeout=(30, 120)
            )
            if response.status_code == 416 and part.exists():
                head = requests.head(url, allow_redirects=True, timeout=30)
                expected = int(head.headers.get("Content-Length", "-1"))
                if expected == part.stat().st_size:
                    part.replace(target)
                    response = None
                    break
            response.raise_for_status()
            append = bool(part.exists() and response.status_code == 206)
            if not append and part.exists():
                part.unlink()
            with part.open("ab" if append else "wb") as handle:
                for chunk in response.iter_content(chunk_size=1024 * 1024):
                    if chunk:
                        handle.write(chunk)
            part.replace(target)
            break
        except Exception as exc:  # retry leaves only .part behind
            last_error = exc
            if response is not None:
                response.close()
            response = None
            if attempt == retries:
                raise RuntimeError(
                    f"download failed dataset={dataset['dataset_id']} "
                    f"after {retries} attempts; partial file is {part}"
                ) from exc
    if not target.exists():
        raise RuntimeError(f"download did not produce file: {target}") from last_error

    response_headers = {} if response is None else response.headers
    record = {
        "datasetId": dataset["dataset_id"],
        "sourceName": dataset["source_name"],
        "productName": dataset["product_name"],
        "version": dataset.get("source_version", "UNKNOWN"),
        "retrievedAt": utc_now(),
        "officialPage": dataset["official_page"],
        "downloadUrl": url,
        "finalUrl": url if response is None else response.url,
        "httpStatus": None if response is None else response.status_code,
        "etag": response_headers.get("ETag"),
        "lastModified": response_headers.get("Last-Modified"),
        "fileName": target.name,
        "fileSize": target.stat().st_size,
        "sha256": sha256_file(target),
        "format": dataset["expected_format"],
        "sourceCrs": dataset.get("source_crs", "UNKNOWN"),
        "license": dataset["license"],
        "attribution": dataset["attribution"],
        "manualDownload": False,
        "rawPath": dataset["raw_target_path"],
    }
    write_json(manifest_file, record)
    return record


def verify_existing(
    repo_root: Path,
    data_root: Path,
    dataset: dict[str, Any],
    target: Path,
    manifest_file: Path,
) -> dict[str, Any]:
    current = json.loads(manifest_file.read_text(encoding="utf-8")) if manifest_file.exists() else {}
    record = {
        "datasetId": dataset["dataset_id"],
        "sourceName": dataset["source_name"],
        "productName": dataset["product_name"],
        "version": dataset.get("source_version", "UNKNOWN"),
        "retrievedAt": current.get("retrievedAt", utc_now()),
        "officialPage": dataset["official_page"],
        "downloadUrl": None,
        "finalUrl": current.get("finalUrl"),
        "httpStatus": None,
        "etag": current.get("etag"),
        "lastModified": current.get("lastModified"),
        "fileName": target.name,
        "fileSize": target.stat().st_size,
        "sha256": sha256_file(target),
        "format": dataset["expected_format"],
        "sourceCrs": dataset.get("source_crs", "UNKNOWN"),
        "license": dataset["license"],
        "attribution": dataset["attribution"],
        "manualDownload": True,
        "rawPath": dataset["raw_target_path"],
    }
    write_json(manifest_file, record)
    return record


def _manual_message(data_root: Path, dataset: dict[str, Any]) -> str:
    return (
        "MANUAL_ACTION_REQUIRED\n"
        f"数据源：{dataset['source_name']}\n"
        f"产品名称：{dataset['product_name']}\n"
        f"目标版本：{dataset.get('source_version', 'UNKNOWN')}\n"
        "阻塞原因：官方下载需要人工接受条款或完成下载表单\n"
        f"官方页面：{dataset['official_page']}\n"
        f"需要下载的准确文件：{dataset.get('expected_file_name', dataset['product_name'])}\n"
        f"期望文件名：{dataset.get('expected_file_name', dataset['product_name'])}\n"
        f"保存到本地路径：{target_path(data_root, dataset)}\n"
        f"文件格式：{dataset['expected_format']}\n"
        f"预计大小：{dataset.get('expected_size_display', 'UNKNOWN')}\n"
        f"许可或署名要求：{dataset['license']}; {dataset['attribution']}\n"
        "人工操作步骤：打开官方页面，接受条款，下载原始文件，不解压、不改名，放入指定目录\n"
        f"下载后校验命令：scene-etl verify-raw --dataset {dataset['dataset_id']}\n"
        "下载后恢复执行命令：scene-etl build --release <release-version>\n"
        "当前其他任务是否可继续：是\n"
        "是否会影响最终快照完整性：是"
    )
