#!/usr/bin/env bash
# Re-run every Sample 1 experiment configuration against Sample 2
# (feedback_2026-10-29.csv / unlabeled real feedback).
#
# Usage:
#   ./scripts/run-sample2-all-evals.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DATA="$ROOT/eval-data/feedback_2026-10-29_eval.ndjson"
PRED_DIR="$ROOT/eval-data/predictions/sample2"
RUNS_JSON="$ROOT/eval-results/runs.json"
JAVA_COMMON=(--data="$DATA" --unlabeled)

mkdir -p "$PRED_DIR"

set_active_run() {
  local run_id="$1"
  python3 - "$RUNS_JSON" "$run_id" <<'PY'
import json, sys
path, run_id = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    doc = json.load(f)
doc["activeDatasetId"] = "feedback-2026-10-29"
doc["activeRunId"] = run_id
with open(path, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(f"Active Sample 2 run -> {run_id}")
PY
}

run_gliner() {
  local threshold="$1"
  local tag="$2"
  local out="$PRED_DIR/gliner-t${tag}.jsonl"
  echo
  echo "======== GLiNER threshold=${threshold} ========"
  python3 scripts/predict_names.py gliner \
    --data "$DATA" \
    --out "$out" \
    --threshold "$threshold"
  ./scripts/run-name-eval.sh gliner "$out" "${JAVA_COMMON[@]}"
}

run_opennlp() {
  local threshold="$1"
  echo
  echo "======== OpenNLP threshold=${threshold} ========"
  ./scripts/run-name-eval.sh opennlp \
    "${JAVA_COMMON[@]}" \
    --opennlp-threshold="$threshold"
}

run_external_no_threshold() {
  local model="$1"
  local out="$PRED_DIR/${model}.jsonl"
  echo
  echo "======== ${model} (no threshold) ========"
  python3 scripts/predict_names.py "$model" --data "$DATA" --out "$out"
  ./scripts/run-name-eval.sh "$model" "$out" "${JAVA_COMMON[@]}"
}

echo "============================================"
echo " Sample 2 full eval suite"
echo " Data: $DATA"
echo "============================================"

# 1) Original run — GLiNER 0.5, OpenNLP none, XLM-RoBERTa, spaCy
set_active_run "original"
run_gliner 0.5 0.5
run_opennlp 0.0
run_external_no_threshold xlm-roberta-ner
run_external_no_threshold spacy-xx

# 2) Threshold 0.7
set_active_run "threshold-0.7"
run_gliner 0.7 0.7
run_opennlp 0.7

# 3) GLiNER 0.6 / OpenNLP 0.5
set_active_run "gliner-0.6-opennlp-0.5"
run_gliner 0.6 0.6
run_opennlp 0.5

# 4) GLiNER 0.55 / OpenNLP 0.3
set_active_run "gliner-0.55-opennlp-0.3"
run_gliner 0.55 0.55
run_opennlp 0.3

# 5) GLiNER 0.4 / OpenNLP 0.2
set_active_run "gliner-0.4-opennlp-0.2"
run_gliner 0.4 0.4
run_opennlp 0.2

echo
echo "Sample 2 suite finished. Open: $ROOT/eval-results/index.html"
