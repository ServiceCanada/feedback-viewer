# Formatting runs in the docker-compose app container, so only Docker is needed.
# With Maven installed locally, `mvn spotless:apply` / `mvn spotless:check` do the same.
MVN = docker-compose run --rm --no-deps app mvn -q

.PHONY: help
help:
	@echo "Available targets:"
	@echo "  fmt        - Format all Java files (Google Java Format)"
	@echo "  fmt-check  - Fail if any Java file needs formatting (what CI runs)"
	@echo "  check-ingress - Validate the decrypted ingress before re-encrypting it"

.PHONY: fmt
fmt:
	@$(MVN) spotless:apply

.PHONY: fmt-check
fmt-check:
	@$(MVN) spotless:check

# CI can't check the ingress (it stays encrypted there), so check it here before
# re-encrypting. The file is piped in on stdin rather than mounted into the container.
INGRESS ?= kubernetes/feedback-viewer-ingress.yml

.PHONY: check-ingress
check-ingress:
	@test -f $(INGRESS) || { echo "$(INGRESS) not found: decrypt it first."; exit 1; }
	@docker run --rm -i ghcr.io/yannh/kubeconform:v0.6.7 -strict -summary < $(INGRESS)
