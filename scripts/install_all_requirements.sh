#!/usr/bin/env bash

echo "Installing all requirements..."

# Find and execute all install scripts in subdirectories of the scripts directory
# Use ripgrep if available, fallback to find
if command -v rg &>/dev/null; then
    install_scripts=$(rg --files --type sh scripts | grep -E 'scripts/.+/install.*\.sh$' | sort)
else
    install_scripts=$(find scripts -mindepth 2 -name "install*.sh" -type f | sort)
fi

if [[ -z "$install_scripts" ]]; then
    echo "No install scripts found in scripts subdirectories"
    exit 0
fi

echo "Found install scripts:"
echo "$install_scripts"
echo ""

# Execute each install script
for script in $install_scripts; do
    if [[ -x "$script" ]]; then
        echo "Running: $script"
        if ! ./"$script"; then
            echo "Error: Failed to execute $script"
            exit 1
        fi
        echo ""
    else
        echo "Warning: $script is not executable, skipping"
    fi
done

echo "All install scripts completed successfully!"
