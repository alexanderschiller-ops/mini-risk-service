#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import shlex
import sys
from pathlib import Path

DIRECTIVE = re.compile(r"^\s*//\s*@tdl\.([A-Za-z0-9_-]+)\s*(.*)$")


class TdlError(RuntimeError):
    pass


def parse_attributes(raw: str, path: Path, line_no: int) -> dict[str, str]:
    attrs: dict[str, str] = {}
    try:
        tokens = shlex.split(raw, posix=True)
    except ValueError as exc:
        raise TdlError(f"{path}:{line_no}: ungueltige Syntax: {exc}") from exc
    for token in tokens:
        if "=" not in token:
            raise TdlError(f"{path}:{line_no}: erwartet key=value: {token!r}")
        key, value = token.split("=", 1)
        if not key or not value:
            raise TdlError(f"{path}:{line_no}: ungueltiges Token: {token!r}")
        attrs[key] = value
    return attrs


def require(attrs: dict[str, str], key: str, context: str) -> str:
    if key not in attrs:
        raise TdlError(f"{context}: Pflichtattribut {key!r} fehlt")
    return attrs[key]


def ensure_job(jobs: dict[str, dict], job_id: str) -> dict:
    return jobs.setdefault(job_id, {
        "id": job_id, "name": None, "description": None,
        "inputStyle": "list", "outputStyle": "list",
        "inputs": [], "outputs": [], "pipeline": [],
        "fieldMappings": [], "target": None, "declared": False,
    })


def ensure_dataset(datasets: dict[str, dict], dataset_id: str) -> dict:
    return datasets.setdefault(dataset_id, {
        "id": dataset_id, "name": None, "description": None,
        "system": None, "domain": None, "version": None,
        "storage": None, "owner": None, "classification": None,
        "fields": [], "producedBy": None, "tags": [], "declared": False,
    })


def collect(source_root: Path) -> tuple[dict[str, dict], dict[str, dict]]:
    jobs: dict[str, dict] = {}
    datasets: dict[str, dict] = {}
    for path in sorted(source_root.rglob("*.java")):
        active_job = None
        active_dataset = None
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = DIRECTIVE.match(line)
            if not match:
                continue
            kind, raw = match.groups()
            attrs = parse_attributes(raw, path, line_no)
            context = f"{path}:{line_no} @tdl.{kind}"

            if kind == "job":
                job_id = require(attrs, "id", context)
                job = ensure_job(jobs, job_id)
                if job["declared"]:
                    raise TdlError(f"{context}: Job mehrfach deklariert")
                job.update(
                    name=require(attrs, "name", context),
                    description=attrs.get("description"),
                    inputStyle=attrs.get("inputStyle", "list"),
                    outputStyle=attrs.get("outputStyle", "list"),
                    declared=True,
                )
                active_job = job_id
                continue

            if kind in {"input", "output", "pipeline", "target", "field-map"}:
                job_id = attrs.pop("job", None) or active_job
                if not job_id:
                    raise TdlError(f"{context}: job=<id> fehlt")
                job = ensure_job(jobs, job_id)
                if kind == "input":
                    job["inputs"].append(require(attrs, "dataset", context))
                elif kind == "output":
                    job["outputs"].append(require(attrs, "dataset", context))
                elif kind == "pipeline":
                    item = {"id": require(attrs, "id", context), "type": require(attrs, "type", context)}
                    for key in ("input", "inputs", "sql", "target", "description"):
                        if key in attrs:
                            item[key] = attrs[key]
                    job["pipeline"].append(item)
                elif kind == "target":
                    job["target"] = dict(attrs)
                else:
                    job["fieldMappings"].append({
                        "source": require(attrs, "source", context),
                        "target": require(attrs, "target", context),
                    })
                continue

            if kind == "dataset":
                dataset_id = require(attrs, "id", context)
                dataset = ensure_dataset(datasets, dataset_id)
                if dataset["declared"]:
                    raise TdlError(f"{context}: Dataset mehrfach deklariert")
                dataset.update(
                    name=require(attrs, "name", context),
                    description=attrs.get("description"),
                    system=attrs.get("system"),
                    domain=attrs.get("domain"),
                    version=attrs.get("version"),
                    owner=attrs.get("owner"),
                    classification=attrs.get("classification"),
                    producedBy=attrs.get("producedBy"),
                    tags=[x for x in attrs.get("tags", "").split(",") if x],
                    declared=True,
                )
                active_dataset = dataset_id
                continue

            if kind in {"storage", "field", "tag"}:
                dataset_id = attrs.pop("dataset", None) or active_dataset
                if not dataset_id:
                    raise TdlError(f"{context}: dataset=<id> fehlt")
                dataset = ensure_dataset(datasets, dataset_id)
                if kind == "storage":
                    dataset["storage"] = dict(attrs)
                elif kind == "field":
                    dataset["fields"].append(dict(attrs))
                else:
                    dataset["tags"].append(require(attrs, "value", context))
                continue

            raise TdlError(f"{context}: unbekannte Direktive")
    return jobs, datasets


def validate(jobs: dict[str, dict], datasets: dict[str, dict]) -> None:
    if not jobs:
        raise TdlError("Keine @tdl.job Kommentare gefunden")
    if not datasets:
        raise TdlError("Keine @tdl.dataset Kommentare gefunden")
    for job_id, job in jobs.items():
        if not job["declared"]:
            raise TdlError(f"Job {job_id} nur referenziert")
        if job["inputStyle"] == "single" and len(job["inputs"]) != 1:
            raise TdlError(f"Job {job_id}: genau ein Input erwartet")
        if job["outputStyle"] == "single" and len(job["outputs"]) != 1:
            raise TdlError(f"Job {job_id}: genau ein Output erwartet")
    for dataset_id, dataset in datasets.items():
        if not dataset["declared"]:
            raise TdlError(f"Dataset {dataset_id} nur referenziert")
        if dataset["storage"] is None:
            raise TdlError(f"Dataset {dataset_id}: storage fehlt")
        if not dataset["fields"]:
            raise TdlError(f"Dataset {dataset_id}: fields fehlen")


def render_pipeline(items: list[dict]) -> list[str]:
    lines = ["pipeline:"]
    for item in items:
        lines += [f"  - id: {item['id']}", f"    type: {item['type']}"]
        if "input" in item:
            lines.append(f"    input: {item['input']}")
        if "inputs" in item:
            lines.append(f"    inputs: [{', '.join(x for x in item['inputs'].split(',') if x)}]")
        if "sql" in item:
            lines.append(f"    sql: {item['sql']}")
        if "target" in item:
            lines.append(f"    target: {item['target']}")
        if "description" in item:
            lines.append(f"    description: {item['description']}")
        lines.append("")
    if lines[-1] == "":
        lines.pop()
    return lines


def render_job(job: dict) -> str:
    lines = ["type: datajob", f"id: {job['id']}", f"name: {job['name']}"]
    if job["description"]:
        lines.append(f"description: {job['description']}")
    lines.append("")
    if job["inputStyle"] == "single":
        lines += ["input:", f"  dataset: {job['inputs'][0]}", "", "output:", f"  dataset: {job['outputs'][0]}", ""]
        if job["pipeline"]:
            lines += render_pipeline(job["pipeline"]) + [""]
    else:
        lines.append("inputs:")
        lines += [f"  - dataset: {x}" for x in job["inputs"]]
        lines.append("")
        if job["pipeline"]:
            lines += render_pipeline(job["pipeline"]) + [""]
        lines.append("outputs:")
        lines += [f"  - dataset: {x}" for x in job["outputs"]]
        lines.append("")
    if job["fieldMappings"]:
        lines.append("fieldMappings:")
        for mapping in job["fieldMappings"]:
            lines += [f"  - source: {mapping['source']}", f"    target: {mapping['target']}"]
        lines.append("")
    if job["target"]:
        lines.append("target:")
        for key in ("type", "format", "location"):
            if key in job["target"]:
                lines.append(f"  {key}: {job['target'][key]}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def render_dataset(dataset: dict) -> str:
    lines = ["type: dataset", f"id: {dataset['id']}", f"name: {dataset['name']}"]
    if dataset["description"]:
        lines.append(f"description: {dataset['description']}")
    lines.append("")
    for key in ("system", "domain", "version"):
        if dataset[key] is not None:
            lines.append(f"{key}: {dataset[key]}")
    lines.append("")
    lines.append("storage:")
    for key in ("type", "database", "schema", "table", "format", "location"):
        if key in dataset["storage"]:
            lines.append(f"  {key}: {dataset['storage'][key]}")
    lines.append("")
    if dataset["owner"] is not None:
        lines.append(f"owner: {dataset['owner']}")
    if dataset["classification"] is not None:
        lines.append(f"classification: {dataset['classification']}")
    lines.append("")
    lines.append("fields:")
    for field in dataset["fields"]:
        lines += [f"  - name: {require(field, 'name', dataset['id'])}", f"    type: {require(field, 'type', dataset['id'])}"]
        if "required" in field:
            lines.append(f"    required: {field['required']}")
        if "primaryKey" in field:
            lines.append(f"    primaryKey: {field['primaryKey']}")
        lines.append("")
    if lines[-1] == "":
        lines.pop()
    if dataset["producedBy"]:
        lines += ["", "lineage:", f"  producedBy: {dataset['producedBy']}"]
    if dataset["tags"]:
        lines += ["", "tags:"] + [f"  - {x}" for x in dataset["tags"]]
    return "\n".join(lines).rstrip() + "\n"


def write_all(destination: Path, jobs: dict[str, dict], datasets: dict[str, dict]) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    names: set[str] = set()
    for job_id, job in sorted(jobs.items()):
        name = f"{job_id}.yaml"
        (destination / name).write_text(render_job(job), encoding="utf-8")
        names.add(name)
    for dataset_id, dataset in sorted(datasets.items()):
        name = f"{dataset_id}.yaml"
        (destination / name).write_text(render_dataset(dataset), encoding="utf-8")
        names.add(name)
    for path in destination.glob("*.yaml"):
        if path.name not in names:
            path.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description="Generiert TDL-YAML aus // @tdl.* Java-Kommentaren")
    parser.add_argument("--source", type=Path, default=Path("src/main/java"))
    parser.add_argument("--output", type=Path, default=Path("tdl-output"))
    parser.add_argument("--contracts", type=Path)
    args = parser.parse_args()
    try:
        jobs, datasets = collect(args.source)
        validate(jobs, datasets)
        write_all(args.output, jobs, datasets)
        if args.contracts:
            write_all(args.contracts, jobs, datasets)
    except TdlError as exc:
        print(f"TDL agent error: {exc}", file=sys.stderr)
        return 2
    print(f"TDL agent: {len(jobs)} Job(s), {len(datasets)} Dataset(s) -> {args.output}")
    if args.contracts:
        print(f"TDL agent: Contracts synchronisiert -> {args.contracts}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
