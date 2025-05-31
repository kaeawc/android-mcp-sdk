#!/usr/bin/env bash

INSTALL_KTFMT_WHEN_MISSING=${INSTALL_KTFMT_WHEN_MISSING:-false}
ONLY_TOUCHED_FILES=${ONLY_TOUCHED_FILES:-true}

# Check if ktfmt is installed
if ! command -v ktfmt &>/dev/null; then
    echo "ktfmt missing"
    if [[ "${INSTALL_KTFMT_WHEN_MISSING}" == "true" ]]; then
      scripts/ktfmt/install_ktfmt.sh
      # Ensure ktfmt is in PATH for subsequent commands
      export PATH="$HOME/bin:$PATH"
    else
      if [[ "$OSTYPE" == "darwin"* ]]; then
        # macos specific advice
        echo "Try 'brew install ktfmt' or run with INSTALL_KTFMT_WHEN_MISSING=true"
      else
        echo "Consult your OS package manager"
      fi
      exit 1
    fi
fi

# Verify ktfmt is available
if ! command -v ktfmt &>/dev/null; then
    echo "Error: ktfmt is not available in PATH"
    exit 1
fi

# Start the timer
start_time=$(bash -c "$(pwd)/scripts/utils/get_timestamp.sh")

# run ktfmt as efficiently as possible against all kt and kts source files
if [[ "${ONLY_TOUCHED_FILES}" == "true" ]]; then
  # use git so we are only checking changed kotlin source files
  echo "Applying ktfmt to git touched Kotlin files..."

  # Get all changed/added/modified Kotlin files
  files=$(git diff --name-only --diff-filter=ACMRT --cached -- '*.kt' '*.kts' 2>/dev/null)
  files+=$'\n'$(git diff --name-only --diff-filter=ACMRT HEAD -- '*.kt' '*.kts' 2>/dev/null)

  # Remove duplicate lines and empty lines
  unique_files=$(echo "$files" | sort | uniq | grep -v '^$')

  if [[ -z "$unique_files" ]]; then
    echo "No Kotlin files have been modified."
    exit 0
  fi

  # Apply ktfmt to the modified files (without --dry-run)
  echo "Formatting $(echo "$unique_files" | wc -l | xargs) files..."
  errors=$(echo "$unique_files" | PATH="$PATH" xargs -n 1 -P "$(nproc 2>/dev/null || echo 4)" ktfmt --kotlinlang-style 2>&1)
  # Filter out "Done formatting" messages which are not actual errors
  errors=$(echo "$errors" | grep -v "^Done formatting" || echo "")

else
  # simply apply ktfmt to all kotlin source files
  echo "Applying ktfmt to all Kotlin files in the project..."

  # Find all Kotlin files
  files=$(find . -type f \( -name "*.kt" -o -name "*.kts" \) -not -path "*/build/*" -not -path "*/.gradle/*")

  if [[ -z "$files" ]]; then
    echo "No Kotlin files found in the project."
    exit 0
  fi

  # Apply ktfmt to all kotlin files (without --dry-run)
  echo "Formatting $(echo "$files" | wc -l | xargs) files..."
  errors=$(echo "$files" | PATH="$PATH" xargs -n 1 -P "$(nproc 2>/dev/null || echo 4)" ktfmt --kotlinlang-style 2>&1)
  # Filter out "Done formatting" messages which are not actual errors
  errors=$(echo "$errors" | grep -v "^Done formatting" || echo "")

fi

# Calculate total elapsed time
end_time=$(bash -c "$(pwd)/scripts/utils/get_timestamp.sh")
total_elapsed=$((end_time - start_time))

# Check and report errors
if [[ -n $errors ]]; then
    echo "Errors in the following files:"
    echo "$errors"
    echo "Total time elapsed: $total_elapsed ms."
    exit 1
fi

echo "Kotlin source files have been formatted successfully."
echo "Total time elapsed: $total_elapsed ms."
