#!/usr/bin/env bash
# Score person-name guesses and write reports under eval-results/.
#
# Usage:
#   ./scripts/run-name-eval.sh opennlp
#   ./scripts/run-name-eval.sh gliner
#   ./scripts/run-name-eval.sh gliner path/to/preds.jsonl --data=... --unlabeled
#
# Extra args after the optional predictions path are forwarded to the Java evaluator
# (e.g. --data=..., --opennlp-threshold=0.5, --unlabeled).
#
# After it finishes, open:
#   eval-results/index.html

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODEL_ID="${1:-opennlp}"
shift || true
PRED_FILE=""
EXTRA_ARGS=()
if [[ $# -gt 0 && "${1}" != --* ]]; then
  PRED_FILE="$1"
  shift
fi
EXTRA_ARGS=("$@")

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

echo "Step 3/3: Scoring comments..."
echo
JAVA_ARGS=("$MODEL_ID")
if [[ -n "${PRED_FILE}" ]]; then
  JAVA_ARGS+=("$PRED_FILE")
fi
JAVA_ARGS+=("${EXTRA_ARGS[@]}")
java -cp "$CP" ca.gc.tbs.eval.NameRedactionBaselineEval "${JAVA_ARGS[@]}"
