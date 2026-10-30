#!/usr/bin/env bash
# Score person-name guesses and write reports under eval-results/.
#
# Usage:
#   ./scripts/run-name-eval.sh opennlp
#   ./scripts/run-name-eval.sh gliner
#
# For external models, create guesses first:
#   python3 scripts/predict_names.py gliner
# or use the one-command wrapper:
#   ./scripts/run-model-eval.sh gliner
#
# After it finishes, open:
#   eval-results/index.html

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODEL_ID="${1:-opennlp}"
PRED_FILE="${2:-}"

echo "============================================"
echo " Name redaction evaluation"
echo "============================================"
echo "Project folder: $ROOT"
echo "Model nickname: $MODEL_ID"
echo
echo "Step 1/3: Compiling the evaluator (this can take a minute the first time)..."
mvn -q test-compile

echo "Step 2/3: Building the Java classpath..."
mvn -q -DincludeScope=test dependency:build-classpath -Dmdep.outputFile=target/eval-classpath.txt
# Drop xercesImpl from this run. The project pins an older Xerces that rejects a
# property OpenNLP 2.5 needs; the JDK built-in XML parser works for evaluation.
FILTERED_CP="$(
  tr ':' '\n' < target/eval-classpath.txt \
    | grep -v -i 'xerces' \
    | paste -sd ':' -
)"
CP="target/classes:target/test-classes:${FILTERED_CP}"

echo "Step 3/3: Scoring all labeled comments..."
echo
if [[ -n "${PRED_FILE}" ]]; then
  java -cp "$CP" ca.gc.tbs.eval.NameRedactionBaselineEval "$MODEL_ID" "$PRED_FILE"
else
  java -cp "$CP" ca.gc.tbs.eval.NameRedactionBaselineEval "$MODEL_ID"
fi
