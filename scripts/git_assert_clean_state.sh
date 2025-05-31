#!/usr/bin/env bash

# Exit if any command fails
set -e

# Check for changes in a single command
CHANGES=$(git status --porcelain | sort)

if [ -z "$CHANGES" ]; then
  echo "Clean git state"
else
  echo "Changes detected, exiting..."
  exit 1
fi