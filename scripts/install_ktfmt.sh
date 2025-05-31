#!/usr/bin/env bash

KTFMT_VERSION="0.54" # Change this to the desired version. Note that Homebrew will always install the latest version

# Check if ktfmt is not installed
if ! command -v ktfmt &>/dev/null; then

  echo "Installing ktfmt $KTFMT_VERSION..."

  # install proper version based on OS and architecture
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    echo "Detected macOS system"
    brew install ktfmt
  else
    # Linux
    echo "Detected Linux system"

    # Check if Java is installed
    if ! command -v java &>/dev/null; then
      echo "Error: Java is required to run ktfmt but is not installed"
      exit 1
    fi

    # Create a temporary directory
    TMP_DIR=$(mktemp -d)
    JAR_PATH="$TMP_DIR/ktfmt.jar"

    # Download ktfmt jar with progress and error handling
    echo "Downloading ktfmt jar..."
    if ! curl -L --fail --show-error -o "$JAR_PATH" "https://github.com/facebook/ktfmt/releases/download/v$KTFMT_VERSION/ktfmt-$KTFMT_VERSION-jar-with-dependencies.jar"; then
      echo "Error: Failed to download ktfmt jar"
      rm -rf "$TMP_DIR"
      exit 1
    fi

    # Verify the jar file exists and has content
    if [ ! -f "$JAR_PATH" ] || [ ! -s "$JAR_PATH" ]; then
      echo "Error: Downloaded jar file is empty or does not exist"
      rm -rf "$TMP_DIR"
      exit 1
    fi

    # Create bin directory if it doesn't exist
    mkdir -p "$HOME/bin"

    # Move jar to a permanent location
    if ! mv "$JAR_PATH" "$HOME/bin/"; then
      echo "Error: Failed to move ktfmt jar to $HOME/bin/"
      rm -rf "$TMP_DIR"
      exit 1
    fi

    # Clean up temporary directory
    rm -rf "$TMP_DIR"

    # Create wrapper script
    if ! cat > "$HOME/bin/ktfmt" << EOF
#!/usr/bin/env bash
java -jar "$HOME/bin/ktfmt.jar" "\$@"
EOF
    then
      echo "Error: Failed to create ktfmt wrapper script"
      exit 1
    fi

    # Make wrapper script executable
    if ! chmod +x "$HOME/bin/ktfmt"; then
      echo "Error: Failed to make ktfmt wrapper script executable"
      exit 1
    fi

    # Add to PATH if not already there
    if [[ ":$PATH:" != *":$HOME/bin:"* ]]; then
      echo "export PATH=\"\$HOME/bin:\$PATH\"" >> "$HOME/.bashrc"
      echo "export PATH=\"\$HOME/bin:\$PATH\"" >> "$HOME/.bash_profile"
      # Add to current PATH immediately
      export PATH="$HOME/bin:$PATH"
    fi

    # Verify installation by checking if the command exists and the jar file is accessible
    if ! command -v ktfmt &>/dev/null || [ ! -f "$HOME/bin/ktfmt.jar" ]; then
      echo "Error: ktfmt installation failed - command or jar file not found"
      exit 1
    fi
  fi

  echo "ktfmt $KTFMT_VERSION installed successfully!"
else
  echo "ktfmt is already installed"
fi