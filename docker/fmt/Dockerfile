# Formatters for everything except Java (Spotless formats Java in the job container).
# Used by `make fmt` and `make fmt-check` through the docker-compose `fmt` service.
FROM node:22.12.0-alpine
RUN npm install -g prettier@3.6.2 @prettier/plugin-xml@3.4.2
COPY --from=ghcr.io/astral-sh/ruff:0.12.0 /ruff /usr/local/bin/ruff
COPY --from=mvdan/shfmt:v3.10.0 /bin/shfmt /usr/local/bin/shfmt
