#!/usr/bin/env bash

INSTALL_ACT_WHEN_MISSING=${INSTALL_ACT_WHEN_MISSING:-false}

# Check if act is installed
if ! command -v act &>/dev/null; then
    echo "act missing"
    if [[ "${INSTALL_ACT_WHEN_MISSING}" == "true" ]]; then
      scripts/install_act.sh
      # Ensure act is in PATH for subsequent commands
      export PATH="$HOME/bin:$PATH"
    else
      if [[ "$OSTYPE" == "darwin"* ]]; then
        # macos specific advice
        echo "Try 'brew install act' or run with INSTALL_ACT_WHEN_MISSING=true"
      else
        echo "Consult your OS package manager or run with INSTALL_ACT_WHEN_MISSING=true"
      fi
      exit 1
    fi
fi

# Verify act is available
if ! command -v act &>/dev/null; then
    echo "Error: act is not available in PATH"
    exit 1
fi

# Check if .github/workflows directory exists
if [ ! -d ".github/workflows" ]; then
    echo "No .github/workflows directory found"
    exit 0
fi

# Check if there are any workflow files
workflow_files=$(find .github/workflows -name "*.yml" -o -name "*.yaml" 2>/dev/null)
if [[ -z "$workflow_files" ]]; then
    echo "No workflow files found in .github/workflows"
    exit 0
fi

echo "Available GitHub Actions jobs and workflows:"
echo "=============================================="
act --list

echo ""
echo "Usage examples:"
echo "  Validate all jobs (dry run):     ./scripts/validate_act.sh"
echo "  Validate specific job:           ACT_JOB=ktfmt ./scripts/validate_act.sh"
echo "  Run pull_request event:          ACT_EVENT=pull_request ACT_DRY_RUN=false ./scripts/apply_act.sh"
echo "  Run with custom Dockerfile:      USE_CUSTOM_DOCKERFILE=true ./scripts/validate_act.sh"