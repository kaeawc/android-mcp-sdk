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
    
    # Check if requirements.txt exists in the script directory (scripts/docs/)
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REQUIREMENTS_FILE="$SCRIPT_DIR/requirements.txt"
    
    if [ -f "$REQUIREMENTS_FILE" ]; then
        print_status "Installing from $REQUIREMENTS_FILE..."
        uv pip install --user -r "$REQUIREMENTS_FILE"
    else
        print_status "Installing individual packages with uv..."
        uv pip install --user mkdocs mkdocs-material mkdocs-minify-plugin mkdocs-git-revision-date-localized-plugin
    fi
}

# Function to check git status
check_git_status() {
    if [ -n "$(git status --porcelain)" ]; then
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
    
    if [ ! -f "mkdocs.yml" ]; then
        print_error "mkdocs.yml not found in current directory"
        exit 1
    fi
    
    # Check if all navigation files exist
    if command_exists mkdocs; then
        if ! mkdocs build --strict --quiet; then
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
    if mkdocs gh-deploy --clean --message "Deploy documentation for commit {sha}"; then
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
    
    mkdocs serve
}

# Main script logic
main() {
    print_status "Android MCP SDK Documentation Deployment"
    print_status "========================================"
    
    # Check if we're in the right directory
    if [ ! -f "mkdocs.yml" ]; then
        print_error "This script must be run from the project root directory (where mkdocs.yml is located)"
        exit 1
    fi
    
    # Parse command line arguments
    COMMAND=${1:-deploy}
    
    case $COMMAND in
        deploy)
            print_status "Deploying documentation to GitHub Pages..."
            
            # Check if MkDocs is installed
            if ! command_exists mkdocs; then
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
            
            if ! command_exists mkdocs; then
                print_warning "MkDocs not found."
                install_mkdocs
            fi
            
            serve_docs
            ;;
            
        build)
            print_status "Building documentation..."
            
            if ! command_exists mkdocs; then
                print_warning "MkDocs not found."
                install_mkdocs
            fi
            
            validate_mkdocs_config
            mkdocs build
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
