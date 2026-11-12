#!/bin/sh
# Formats every file type except Java: `fmt.sh` rewrites files, `fmt.sh --check` only reports.
# Runs inside the fmt image (docker/fmt/Dockerfile); use `make fmt` / `make fmt-check`.

if [ "$1" = "--check" ]; then
  prettier_mode=--check ruff_mode=--check shfmt_mode=-d
else
  prettier_mode=--write ruff_mode= shfmt_mode=-w
fi

# Every formatter runs even if an earlier one fails, so a check reports all problems at once.
status=0

# XML, YAML, Markdown, JSON and JavaScript. Skips .gitignore'd and .prettierignore'd files.
prettier $prettier_mode --log-level warn \
  --plugin=/usr/local/lib/node_modules/@prettier/plugin-xml/src/plugin.js \
  "**/*.{xml,yml,yaml,md,json,js}" || status=1

# Python and shell, wherever they are. Shell settings come from .editorconfig.
files() { find . \( -path ./.git -o -path ./target -o -path ./node_modules -o -name .terragrunt-cache \) -prune -o -name "$1" -print; }
py=$(files '*.py')
sh=$(files '*.sh')
if [ -n "$py" ]; then ruff format $ruff_mode $py || status=1; fi
if [ -n "$sh" ]; then shfmt -l $shfmt_mode $sh || status=1; fi

exit $status
