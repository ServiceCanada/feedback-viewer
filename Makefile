# Java formatter Makefile
# Keep this file locally, don't commit it to the repository

# Variables
FORMATTER_VERSION = 1.17.0
FORMATTER_JAR = google-java-format-$(FORMATTER_VERSION)-all-deps.jar
FORMATTER_URL = https://github.com/google/google-java-format/releases/download/v$(FORMATTER_VERSION)/$(FORMATTER_JAR)
JAVA_FILES = $(shell find src -name "*.java")
YML_FILES = $(shell find . -name "*.yml" -not -path "./target/*" -not -path "./node_modules/*")

# Default target
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  fmt        - Format all Java and YAML files"
	@echo "  format     - Format all Java files using Google Java Format"
	@echo "  fmt-yml    - Format all YAML files using Prettier"
	@echo "  fmt-ips    - Format IP whitelist in ingress YAML"
	@echo "  clean      - Remove the formatter jar"
	@echo ""
	@echo "Note: Keep this Makefile locally, don't commit it to your repository"

# Download the formatter if it doesn't exist
$(FORMATTER_JAR):
	@echo "Downloading Google Java Format..."
	@curl -L $(FORMATTER_URL) -o $(FORMATTER_JAR)

# Format all Java and YAML files
.PHONY: fmt
fmt: format fmt-yml fmt-ips

# Format YAML files using Prettier
.PHONY: fmt-yml
fmt-yml:
	@echo "Formatting YAML files..."
	@npx --yes prettier --ignore-path /dev/null --write $(YML_FILES)
	@echo "YAML formatting complete!"

# Format IP whitelist in ingress YAML
.PHONY: fmt-ips
fmt-ips:
	@echo "Formatting IP whitelist..."
	@python3 scripts/fmt-ips.py
	@echo "IP formatting complete!"

# Format all Java files
.PHONY: format
format: $(FORMATTER_JAR)
	@echo "Formatting Java files..."
	@java -jar $(FORMATTER_JAR) --replace $(JAVA_FILES)
	@echo "Formatting complete!"

# Check if files need formatting without changing them
.PHONY: check
check: $(FORMATTER_JAR)
	@echo "Checking Java files for formatting issues..."
	@java -jar $(FORMATTER_JAR) --dry-run --set-exit-if-changed $(JAVA_FILES)

# Clean up
.PHONY: clean
clean:
	@echo "Removing formatter jar..."
	@rm -f $(FORMATTER_JAR)