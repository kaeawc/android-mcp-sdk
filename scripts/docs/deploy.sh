#!/bin/bash

# Deploy documentation to GitHub Pages
# This script builds the MkDocs site and deploys it to GitHub Pages

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to install uv if not present
install_uv() {
    print_status "Installing uv package manager..."
    
    if command_exists curl; then
        curl -LsSf https://astral.sh/uv/install.sh | sh
        # Source the shell to get uv in PATH
        # shellcheck disable=SC1091
        source "$HOME"/.cargo/env 2>/dev/null || true
        export PATH="$HOME/.cargo/bin:$PATH"
    else
        print_error "curl not found. Please install curl first or install uv manually."
        print_status "You can install uv from: https://github.com/astral-sh/uv"
        exit 1
    fi
}

# Function to install MkDocs if not present
install_mkdocs() {
    print_status "Installing MkDocs and dependencies..."
    
    # Check if uv is available
    if ! command_exists uv; then
        print_warning "uv not found. Installing uv..."
        install_uv
    fi
    
    # Get the script directory and check if we have a uv project
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    if [ -f "$SCRIPT_DIR/pyproject.toml" ] && [ -f "$SCRIPT_DIR/uv.lock" ]; then
        print_status "Installing from uv project..."
        cd "$SCRIPT_DIR"
        uv sync
    else
        print_status "Installing individual packages with uv..."
        uv tool install mkdocs
        uv tool install mkdocs-material
        uv tool install mkdocs-minify-plugin
        uv tool install mkdocs-git-revision-date-localized-plugin
    fi
}

# Function to check if we have a uv project
has_uv_project() {
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    [ -f "$SCRIPT_DIR/pyproject.toml" ] && [ -f "$SCRIPT_DIR/uv.lock" ]
}

# Function to run mkdocs command
run_mkdocs() {
    if has_uv_project; then
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        (cd "$SCRIPT_DIR" && uv run mkdocs "$@")
    else
        mkdocs "$@"
    fi
}

# Function to check git status
check_git_status() {
    # Check git status from project root if we're in a uv project
    if has_uv_project; then
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        GIT_STATUS=$(cd "$SCRIPT_DIR/../.." && git status --porcelain)
    else
        GIT_STATUS=$(git status --porcelain)
    fi

    if [ -n "$GIT_STATUS" ]; then
        print_warning "You have uncommitted changes. It's recommended to commit them before deploying docs."
        read -p "Do you want to continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_status "Deployment cancelled."
            exit 0
        fi
    fi
}

# Function to validate mkdocs configuration
validate_mkdocs_config() {
    print_status "Validating MkDocs configuration..."

    if has_uv_project; then
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        CONFIG_FILE="$SCRIPT_DIR/../../mkdocs.yml"
        if [ ! -f "$CONFIG_FILE" ]; then
            print_error "mkdocs.yml not found at $CONFIG_FILE"
            exit 1
        fi
        CONFIG_FILE="../../mkdocs.yml"  # Relative path for mkdocs command
    else
        CONFIG_FILE="mkdocs.yml"
        if [ ! -f "$CONFIG_FILE" ]; then
            print_error "mkdocs.yml not found in current directory"
            exit 1
        fi
    fi

    # Check if all navigation files exist
    if command_exists mkdocs || has_uv_project; then
        if ! run_mkdocs build --strict --quiet --config-file "$CONFIG_FILE"; then
            print_error "MkDocs build failed. Please fix configuration errors."
            exit 1
        fi
        print_status "MkDocs configuration is valid"
    else
        print_warning "MkDocs not found, skipping validation"
    fi
}

# Function to build and deploy
deploy_docs() {
    print_status "Building and deploying documentation to GitHub Pages..."

    # Set environment variables for analytics (optional)
    if [ -n "$GOOGLE_ANALYTICS_KEY" ]; then
        export GOOGLE_ANALYTICS_KEY="$GOOGLE_ANALYTICS_KEY"
    fi

    # Deploy to gh-pages branch
    if has_uv_project; then
        CONFIG_FILE="../../mkdocs.yml"
    else
        CONFIG_FILE="mkdocs.yml"
    fi

    if run_mkdocs gh-deploy --clean --message "Deploy documentation for commit {sha}" --config-file "$CONFIG_FILE"; then
        print_status "Documentation deployed successfully!"
        print_status "Your documentation will be available at:"

        # Try to get the GitHub Pages URL
        REPO_URL=$(git config --get remote.origin.url)
        if [[ $REPO_URL == *"github.com"* ]]; then
            # Extract username/repo from URL
            if [[ $REPO_URL == *".git" ]]; then
                REPO_PATH=${REPO_URL%.git}
            else
                REPO_PATH=$REPO_URL
            fi

            if [[ $REPO_PATH == *"github.com/"* ]]; then
                USER_REPO=${REPO_PATH##*/github.com/}
                USER_REPO=${USER_REPO/:/\/}  # Replace : with / for SSH URLs
                echo "  https://${USER_REPO%/*}.github.io/${USER_REPO##*/}/"
            fi
        fi

        print_status "Note: It may take a few minutes for changes to appear on GitHub Pages."
    else
        print_error "Deployment failed!"
        exit 1
    fi
}

# Function to serve docs locally for testing
serve_docs() {
    print_status "Starting local documentation server..."
    print_status "Documentation will be available at: http://127.0.0.1:8000"
    print_status "Press Ctrl+C to stop the server"

    if has_uv_project; then
        CONFIG_FILE="../../mkdocs.yml"
    else
        CONFIG_FILE="mkdocs.yml"
    fi

    run_mkdocs serve --config-file "$CONFIG_FILE"
}

# Main script logic
main() {
    print_status "Android MCP SDK Documentation Deployment"
    print_status "========================================"

    # Parse command line arguments
    COMMAND=${1:-deploy}

    case $COMMAND in
        deploy)
            print_status "Deploying documentation to GitHub Pages..."

            # Check if MkDocs is installed or we have a uv project
            if ! command_exists mkdocs && ! has_uv_project; then
                print_warning "MkDocs not found."
                read -p "Do you want to install MkDocs and dependencies? (y/N): " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    install_mkdocs
                else
                    print_error "MkDocs is required for deployment. Please install it first."
                    exit 1
                fi
            fi

            # Check git status
            check_git_status

            # Validate configuration
            validate_mkdocs_config

            # Deploy
            deploy_docs
            ;;

        serve|preview)
            print_status "Starting local documentation server for preview..."

            if ! command_exists mkdocs && ! has_uv_project; then
                print_warning "MkDocs not found."
                install_mkdocs
            fi

            serve_docs
            ;;

        build)
            print_status "Building documentation..."

            if ! command_exists mkdocs && ! has_uv_project; then
                print_warning "MkDocs not found."
                install_mkdocs
            fi

            validate_mkdocs_config
            
            if has_uv_project; then
                CONFIG_FILE="../../mkdocs.yml"
            else
                CONFIG_FILE="mkdocs.yml"
            fi
            
            run_mkdocs build --config-file "$CONFIG_FILE"
            print_status "Documentation built in site/ directory"
            ;;
            
        install)
            install_mkdocs
            print_status "MkDocs and dependencies installed successfully"
            ;;
            
        help|--help|-h)
            echo "Usage: $0 [command]"
            echo
            echo "Commands:"
            echo "  deploy    Deploy documentation to GitHub Pages (default)"
            echo "  serve     Serve documentation locally for preview"
            echo "  build     Build documentation locally"
            echo "  install   Install MkDocs and dependencies using uv"
            echo "  help      Show this help message"
            echo
            echo "Environment variables:"
            echo "  GOOGLE_ANALYTICS_KEY    Google Analytics tracking ID (optional)"
            echo
            echo "Dependencies:"
            echo "  This script uses uv (https://github.com/astral-sh/uv) for package management."
            echo "  If uv is not installed, the script will attempt to install it automatically."
            echo
            echo "Automatic Deployment:"
            echo "  Documentation is automatically deployed via GitHub Actions when changes"
            echo "  are pushed to the main branch and all other commit jobs pass successfully."
            echo "  Manual deployment using this script is typically only needed for testing."
            ;;
            
        *)
            print_error "Unknown command: $COMMAND"
            echo "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
