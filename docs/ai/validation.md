# Validation and Development Rules

This document contains all validation and development rules for the Android MCP SDK project.

## Project Overview

This is an Android library project to enable running MCP Servers hosted by Android apps.
The goal of the project is to expose an MCP server to an Android engineer running an MCP client on
their adb connected workstation.

## Building

Never run clean unless the Gradle build cache/outputs seem corrupted. Usually that's from annotation processing.

- **Build the library**: `./gradlew :lib:compileDebugKotlin`
- **Build the project**: `./gradlew buildDebug`
- **Android Lint the project**: `./gradlew lint`

## Android Studio IDE Actions

When working in Android Studio, these action IDs can be used to perform IDE operations:

- **Gradle Sync**: Use action ID `Android.SyncProject` to synchronize the project with Gradle files

### Automatic Gradle Sync Rule

- **When to sync**: If any Gradle version catalog (`gradle/libs.versions.toml`) or build files (
  `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`) have changed, a
  Gradle sync should be attempted if none has occurred within the last minute
- **Files that trigger sync**:
  - `gradle/libs.versions.toml`
  - `build.gradle` / `build.gradle.kts` (any module)
  - `settings.gradle` / `settings.gradle.kts`
  - `gradle.properties`

## Code Formatting

ktfmt should be used for lint.

### Kotlin Formatting

- **Format Kotlin code**: `./scripts/ktfmt/apply_ktfmt.sh`
    - Use `ONLY_TOUCHED_FILES=false` to format all files
- **Validate Kotlin formatting**: `./scripts/ktfmt/validate_ktfmt.sh`
    - Use `ONLY_TOUCHED_FILES=false` to check all files
- **Install ktfmt if missing**: `./scripts/ktfmt/install_ktfmt.sh`
    - Or set `INSTALL_KTFMT_WHEN_MISSING=true` when running other scripts

## Validation Scripts

### Shell Scripts

- **Validate shell scripts**: `./scripts/shellcheck/validate_shell_scripts.sh`

### XML Files

- **Validate XML files**: `./scripts/xml/validate_xml.sh`

### Git State

- **Check git clean state**: `./scripts/git/git_assert_clean_state.sh`

## Installation Scripts

### All Requirements

- **Install all requirements at once**: `./scripts/install_all_requirements.sh`

### Individual Tools

- **Install ripgrep if missing**: `./scripts/ripgrep/install_ripgrep.sh`
    - Or set `INSTALL_RIPGREP_WHEN_MISSING=true` when running other scripts

## GitHub Actions Testing with act

act should be used for local GitHub Actions testing.

### Installation

- **Install act if missing**: `./scripts/act/install_act.sh`
    - Or set `INSTALL_ACT_WHEN_MISSING=true` when running other scripts

### Usage

- **List available GitHub Actions jobs**: `./scripts/act/act_list.sh`
- **Validate GitHub Actions workflows locally**: `./scripts/act/validate_act.sh`
    - Use `ACT_JOB=job-name` for specific jobs
- **Run GitHub Actions workflows locally**: `./scripts/act/apply_act.sh`
    - Use `ACT_EVENT=pull_request`, `ACT_JOB=job-name` for specific tests

### Examples

```bash
ACT_JOB=ktfmt ./scripts/act/validate_act.sh
ACT_EVENT=pull_request ./scripts/act/apply_act.sh
```

### Custom Dockerfile

- **Use custom Dockerfile for act**: `USE_CUSTOM_DOCKERFILE=true ./scripts/act/validate_act.sh`

## Important Notes

- **When making changes to .github/workflows files**: Always test locally with act before pushing
- **CI Environment**: The ci/Dockerfile provides a complete Android development environment with
  Azul JDK 23, Android SDK, ktfmt, act, xmlstarlet, and shellcheck (optional)
