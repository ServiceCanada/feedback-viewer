# Formatting runs in the docker-compose app container, so only Docker is needed.
# With Maven installed locally, `mvn spotless:apply` / `mvn spotless:check` do the same.
MVN = docker-compose run --rm --no-deps app mvn -q

.PHONY: help
help:
	@echo "Available targets:"
	@echo "  fmt        - Format all Java files (Google Java Format)"
	@echo "  fmt-check  - Fail if any Java file needs formatting (what CI runs)"

.PHONY: fmt
fmt:
	@$(MVN) spotless:apply

.PHONY: fmt-check
fmt-check:
	@$(MVN) spotless:check

