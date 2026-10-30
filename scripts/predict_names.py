#!/usr/bin/env python3
"""
Create person-name guesses for the labeled evaluation comments.

Usage:
  python3 scripts/predict_names.py gliner
  python3 scripts/predict_names.py xlm-roberta-ner
  python3 scripts/predict_names.py spacy-xx

Writes:
  eval-data/predictions/<model>.jsonl
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


SUPPORTED = ("gliner", "xlm-roberta-ner", "spacy-xx")


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def load_comments(path: Path) -> list[dict]:
    comments = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            row = json.loads(line)
            comments.append(
                {
                    "id": row["_id"],
                    "text": row["problemDetails"],
                    "language": row.get("language", ""),
                }
            )
    return comments


def write_predictions(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def predict_gliner(comments: list[dict]) -> list[dict]:
    try:
        from gliner import GLiNER
    except ImportError as exc:
        raise SystemExit(
            "Missing package 'gliner'. Install with:\n"
            "  python3 -m pip install -r scripts/requirements-eval.txt"
        ) from exc

    print("Loading GLiNER model urchade/gliner_multi-v2.1 (first time may download)...")
    model = GLiNER.from_pretrained("urchade/gliner_multi-v2.1")
    labels = ["person"]
    out = []
    total = len(comments)
    for i, comment in enumerate(comments, start=1):
        if i == 1 or i % 50 == 0 or i == total:
            print(f"  GLiNER progress: {i}/{total}")
        try:
            ents = model.predict_entities(comment["text"], labels, threshold=0.5)
            spans = []
            for ent in ents:
                start = int(ent["start"])
                end = int(ent["end"])
                spans.append(
                    {
                        "start": start,
                        "end": end,
                        "text": comment["text"][start:end],
                    }
                )
            out.append({"id": comment["id"], "spans": spans, "error": None})
        except Exception as exc:  # noqa: BLE001 - record per-comment failures
            out.append(
                {
                    "id": comment["id"],
                    "spans": [],
                    "error": f"{type(exc).__name__}: {exc}",
                }
            )
    return out


def predict_xlm_roberta(comments: list[dict]) -> list[dict]:
    try:
        from transformers import pipeline
    except ImportError as exc:
        raise SystemExit(
            "Missing package 'transformers' (and usually 'torch'). Install with:\n"
            "  python3 -m pip install -r scripts/requirements-eval.txt"
        ) from exc

    model_name = "Davlan/xlm-roberta-base-ner-hrl"
    print(f"Loading Hugging Face NER pipeline {model_name} (first time may download)...")
    ner = pipeline(
        "token-classification",
        model=model_name,
        aggregation_strategy="simple",
    )
    keep = {"PER", "PERSON", "B-PER", "I-PER"}
    out = []
    total = len(comments)
    for i, comment in enumerate(comments, start=1):
        if i == 1 or i % 50 == 0 or i == total:
            print(f"  XLM-RoBERTa progress: {i}/{total}")
        try:
            ents = ner(comment["text"])
            spans = []
            for ent in ents:
                label = str(ent.get("entity_group") or ent.get("entity") or "").upper()
                if label not in keep and not label.endswith("PER"):
                    continue
                start = int(ent["start"])
                end = int(ent["end"])
                spans.append(
                    {
                        "start": start,
                        "end": end,
                        "text": comment["text"][start:end],
                    }
                )
            out.append({"id": comment["id"], "spans": spans, "error": None})
        except Exception as exc:  # noqa: BLE001
            out.append(
                {
                    "id": comment["id"],
                    "spans": [],
                    "error": f"{type(exc).__name__}: {exc}",
                }
            )
    return out


def predict_spacy_xx(comments: list[dict]) -> list[dict]:
    try:
        import spacy
    except ImportError as exc:
        raise SystemExit(
            "Missing package 'spacy'. Install with:\n"
            "  python3 -m pip install -r scripts/requirements-eval.txt\n"
            "Then download the model:\n"
            "  python3 -m spacy download xx_ent_wiki_sm"
        ) from exc

    model_name = "xx_ent_wiki_sm"
    print(f"Loading spaCy model {model_name}...")
    try:
        nlp = spacy.load(model_name)
    except OSError as exc:
        raise SystemExit(
            f"spaCy model '{model_name}' is not installed.\n"
            "Download it with:\n"
            f"  python3 -m spacy download {model_name}"
        ) from exc

    out = []
    total = len(comments)
    for i, comment in enumerate(comments, start=1):
        if i == 1 or i % 50 == 0 or i == total:
            print(f"  spaCy progress: {i}/{total}")
        try:
            doc = nlp(comment["text"])
            spans = []
            for ent in doc.ents:
                if ent.label_ != "PER" and ent.label_ != "PERSON":
                    continue
                spans.append(
                    {
                        "start": ent.start_char,
                        "end": ent.end_char,
                        "text": comment["text"][ent.start_char : ent.end_char],
                    }
                )
            out.append({"id": comment["id"], "spans": spans, "error": None})
        except Exception as exc:  # noqa: BLE001
            out.append(
                {
                    "id": comment["id"],
                    "spans": [],
                    "error": f"{type(exc).__name__}: {exc}",
                }
            )
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="Create person-name predictions for evaluation.")
    parser.add_argument(
        "model",
        choices=SUPPORTED,
        help="Which single model should create the guesses",
    )
    args = parser.parse_args()

    root = project_root()
    data_path = root / "eval-data" / "synthetic_names_pi_test.ndjson"
    out_path = root / "eval-data" / "predictions" / f"{args.model}.jsonl"

    if not data_path.exists():
        print(f"Missing labeled data file: {data_path}", file=sys.stderr)
        return 1

    print(f"Reading comments from: {data_path}")
    comments = load_comments(data_path)
    print(f"Comments loaded: {len(comments)}")
    print(f"Model nickname: {args.model}")
    print()

    if args.model == "gliner":
        rows = predict_gliner(comments)
    elif args.model == "xlm-roberta-ner":
        rows = predict_xlm_roberta(comments)
    else:
        rows = predict_spacy_xx(comments)

    write_predictions(out_path, rows)
    errors = sum(1 for row in rows if row.get("error"))
    print()
    print(f"Wrote predictions: {out_path}")
    print(f"Rows: {len(rows)}  (prediction errors recorded: {errors})")
    print("Next step: score them with:")
    print(f"  ./scripts/run-name-eval.sh {args.model}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
