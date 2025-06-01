# Documentation Infrastructure

This directory contains the infrastructure for building and deploying the Android MCP SDK
documentation.

## Files

- **`deploy.sh`** - Main deployment script for building and deploying documentation
- **`requirements.txt`** - Python dependencies for MkDocs and plugins
- **`README.md`** - This documentation file

## Quick Start

### Local Development

```bash
# Install dependencies and serve documentation locally
./scripts/docs/deploy.sh serve

# Or build documentation without serving
./scripts/docs/deploy.sh build
```

### Deployment

```bash
# Deploy to GitHub Pages
./scripts/docs/deploy.sh deploy
```

## Dependencies

This infrastructure uses [uv](https://github.com/astral-sh/uv) for fast Python package management
instead of pip. The deployment script will automatically install uv if it's not present.

### Why uv?

- **Faster**: Significantly faster package installation than pip
- **Better resolver**: More reliable dependency resolution
- **Drop-in replacement**: Compatible with pip workflows
- **Modern**: Built with modern Python packaging standards

## Commands

The deployment script supports several commands:

```bash
./scripts/docs/deploy.sh [command]
```

Available commands:

- **`deploy`** (default) - Deploy documentation to GitHub Pages
- **`serve`** - Serve documentation locally for preview at http://127.0.0.1:8000
- **`build`** - Build documentation locally (output in `site/` directory)
- **`install`** - Install MkDocs and dependencies using uv
- **`help`** - Show help message

## Environment Variables

- **`GOOGLE_ANALYTICS_KEY`** - Optional Google Analytics tracking ID for the documentation site

## Automatic Deployment

Documentation is automatically deployed as part of the main commit workflow when:

- Changes are pushed to the `main` branch
- All other commit jobs (tests, linting, builds) pass successfully
- The commit includes changes to documentation files

The deployment job:

1. **Waits for all other jobs** to complete successfully
2. **Only runs on main branch** pushes (not PRs)
3. **Validates** documentation builds correctly
4. **Deploys** to GitHub Pages automatically

### Workflow Integration

The documentation deployment is integrated into `.github/workflows/commit.yml` as a `deploy-docs`
job that:

- **Depends on**: `unit-tests`, `validate-xml`, `ktfmt`, `validate-shell-scripts`, `build-library`,
  `build-apk`
- **Runs only if**: All dependent jobs succeed AND it's a push to main branch
- **Has permissions**: To write to GitHub Pages
- **Uses environment**: `github-pages` for deployment tracking

## Manual Setup

If you prefer to set up the environment manually:

```bash
# Install uv
curl -LsSf https://astral.sh/uv/install.sh | sh

# Install documentation dependencies
uv pip install -r scripts/docs/requirements.txt

# Serve documentation
mkdocs serve

# Deploy to GitHub Pages
mkdocs gh-deploy
```

## Troubleshooting

### uv Installation Issues

If automatic uv installation fails:

1. Install uv manually: https://github.com/astral-sh/uv#installation
2. Ensure `curl` is installed for the automatic installer
3. Check that `~/.cargo/bin` is in your PATH

### MkDocs Build Errors

1. **Configuration errors**: Check `mkdocs.yml` syntax
2. **Missing files**: Ensure all navigation files exist
3. **Plugin issues**: Verify all plugins in `requirements.txt` are installed

### GitHub Pages Deployment Issues

1. **Permissions**: Ensure repository has Pages enabled
2. **Branch**: Check that `gh-pages` branch exists and is set as Pages source
3. **Build failures**: Check GitHub Actions logs for detailed error messages

## File Structure

```
scripts/docs/
├── deploy.sh          # Main deployment script
├── requirements.txt   # Python dependencies
└── README.md         # This file

docs/                  # Documentation source files
├── index.md          # Homepage
├── getting-started.md
├── usage.md
├── transport.md
├── api-reference.md
└── ai/               # AI-related documentation
    ├── implementation-summary.md
    └── validation.md

mkdocs.yml            # MkDocs configuration
```

## Contributing

When making changes to the documentation infrastructure:

1. **Test locally** with `./scripts/docs/deploy.sh serve`
2. **Validate build** with `./scripts/docs/deploy.sh build`
3. **Update requirements.txt** if adding new MkDocs plugins
4. **Test deployment** to ensure GitHub Pages works correctly
