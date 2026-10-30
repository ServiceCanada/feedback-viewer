#!/usr/bin/env bash
# One-command evaluate for an external single model:
#   1) create guesses with Python
#   2) score those guesses with the same Java report writer used for OpenNLP
#
# Usage:
#   ./scripts/run-model-eval.sh gliner
#   ./scripts/run-model-eval.sh xlm-roberta-ner
#   ./scripts/run-model-eval.sh spacy-xx
#
# For the original OpenNLP baseline, use:
#   ./scripts/run-name-eval.sh opennlp

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODEL_ID="${1:-}"
if [[ -z "${MODEL_ID}" ]]; then
  echo "Usage: ./scripts/run-model-eval.sh <gliner|xlm-roberta-ner|spacy-xx>"
  exit 1
fi

case "${MODEL_ID}" in
  gliner|xlm-roberta-ner|spacy-xx) ;;
  opennlp)
    echo "For OpenNLP, run: ./scripts/run-name-eval.sh opennlp"
    exit 1
    ;;
  *)
    echo "Unknown model nickname: ${MODEL_ID}"
    echo "Supported: gliner, xlm-roberta-ner, spacy-xx"
    exit 1
    ;;
esac

echo "============================================"
echo " Model evaluation: ${MODEL_ID}"
echo "============================================"
echo "Project folder: $ROOT"
echo
echo "Step A: Creating guesses with Python..."
python3 scripts/predict_names.py "${MODEL_ID}"
echo
echo "Step B: Scoring guesses and writing reports..."
./scripts/run-name-eval.sh "${MODEL_ID}"
