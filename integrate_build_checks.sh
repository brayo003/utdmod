#!/bin/bash

# Integrate Build Checks with Major Change Detection
# Hooks into Git and development workflow for automated build verification

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
BUILD_CHECKER="$PROJECT_ROOT/gradle_build_checker.sh"
BUILD_MONITOR="$PROJECT_ROOT/build_monitor.sh"

# Configuration
AUTO_BUILD_ON_COMMIT=${AUTO_BUILD_ON_COMMIT:-true}
AUTO_BUILD_ON_PUSH=${AUTO_BUILD_ON_PUSH:-false}
BUILD_ON_FILE_CHANGE=${BUILD_ON_FILE_CHANGE:-true}
MONITOR_CHANGES=${MONITOR_CHANGES:-true}

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INTEGRATION]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Install Git hooks
install_git_hooks() {
    log_info "Installing Git hooks for automated build checks..."
    
    local hooks_dir="$PROJECT_ROOT/.git/hooks"
    mkdir -p "$hooks_dir"
    
    # Pre-commit hook
    cat << 'EOF' > "$hooks_dir/pre-commit"
#!/bin/bash
# Pre-commit hook for automated build checks

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
BUILD_CHECKER="$PROJECT_ROOT/gradle_build_checker.sh"

echo "Running pre-commit build check..."

if [[ -f "$BUILD_CHECKER" ]] && [[ "$AUTO_BUILD_ON_COMMIT" == "true" ]]; then
    if "$BUILD_CHECKER" pre_commit_check; then
        echo "Pre-commit build check passed"
        exit 0
    else
        echo "Pre-commit build check failed - commit blocked"
        echo "Run '$BUILD_CHECKER' manually to fix issues"
        exit 1
    fi
else
    echo "Build check skipped"
    exit 0
fi
EOF
    
    # Pre-push hook
    cat << 'EOF' > "$hooks_dir/pre-push"
#!/bin/bash
# Pre-push hook for automated build checks

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
BUILD_CHECKER="$PROJECT_ROOT/gradle_build_checker.sh"

echo "Running pre-push build check..."

if [[ -f "$BUILD_CHECKER" ]] && [[ "$AUTO_BUILD_ON_PUSH" == "true" ]]; then
    if "$BUILD_CHECKER" pre_push_check; then
        echo "Pre-push build check passed"
        exit 0
    else
        echo "Pre-push build check failed - push blocked"
        echo "Run '$BUILD_CHECKER' manually to fix issues"
        exit 1
    fi
else
    echo "Pre-push build check skipped"
    exit 0
fi
EOF
    
    # Make hooks executable
    chmod +x "$hooks_dir/pre-commit"
    chmod +x "$hooks_dir/pre-push"
    
    log_success "Git hooks installed"
}

# Setup file watching
setup_file_watcher() {
    log_info "Setting up file change monitoring..."
    
    # Create file watcher script
    cat << 'EOF' > "$PROJECT_ROOT/file_watcher.sh"
#!/bin/bash
# File change watcher for automated builds

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_CHECKER="$PROJECT_ROOT/gradle_build_checker.sh"

# Monitor Java source files and build configuration
watch_files() {
    echo "Starting file watcher... (Ctrl+C to stop)"
    
    while true; do
        # Wait for file changes
        inotifywait -r -e modify,create,delete --include '\.(java|gradle|properties)$' \
            "$PROJECT_ROOT/src" "$PROJECT_ROOT/build.gradle" "$PROJECT_ROOT/gradle.properties" 2>/dev/null || \
        {
            echo "inotifywait not available, falling back to polling..."
            while true; do
                sleep 30
                echo "Polling for changes..."
                break
            done
        }
        
        echo "File change detected - triggering build check..."
        
        if [[ -f "$BUILD_CHECKER" ]] && [[ "$BUILD_ON_FILE_CHANGE" == "true" ]]; then
            "$BUILD_CHECKER" file_change_trigger || true
        fi
    done
}

watch_files
EOF
    
    chmod +x "$PROJECT_ROOT/file_watcher.sh"
    log_success "File watcher setup complete"
}

# Setup IDE integration scripts
setup_ide_integration() {
    log_info "Setting up IDE integration..."
    
    # VS Code tasks
    local vscode_dir="$PROJECT_ROOT/.vscode"
    mkdir -p "$vscode_dir"
    
    cat << 'EOF' > "$vscode_dir/tasks.json"
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "UTD Build Check",
            "type": "shell",
            "command": "./gradle_build_checker.sh",
            "args": ["ide_trigger"],
            "group": "build",
            "presentation": {
                "echo": true,
                "reveal": "always",
                "focus": false,
                "panel": "new"
            },
            "problemMatcher": {
                "owner": "gradle",
                "fileLocation": "relative",
                "pattern": {
                    "regexp": "^(.*):(\\d+):\\s+(error|warning):\\s+(.*)$",
                    "file": 1,
                    "line": 2,
                    "severity": 3,
                    "message": 4
                }
            }
        },
        {
            "label": "UTD Build Monitor",
            "type": "shell",
            "command": "./build_monitor.sh",
            "args": ["once"],
            "group": "build",
            "presentation": {
                "echo": true,
                "reveal": "always",
                "focus": false,
                "panel": "new"
            }
        }
    ]
}
EOF
    
    # VS Code launch configuration for debugging
    cat << 'EOF' > "$vscode_dir/launch.json"
{
    "version": "0.2.0",
    "configurations": [
        {
            "name": "Debug UTD Mod",
            "type": "java",
            "request": "attach",
            "hostName": "localhost",
            "port": 5005,
            "projectName": "utdmod"
        }
    ]
}
EOF
    
    log_success "VS Code integration configured"
}

# Setup continuous integration
setup_ci_integration() {
    log_info "Setting up CI integration..."
    
    # GitHub Actions workflow
    local github_dir="$PROJECT_ROOT/.github/workflows"
    mkdir -p "$github_dir"
    
    cat << 'EOF' > "$github_dir/build-check.yml"
name: UTD Mod Build Check

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build-check:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Run build check
      run: ./gradle_build_checker.sh ci_build
    
    - name: Upload build results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: build-results
        path: |
          build_json/
          build_logs/
        retention-days: 7
EOF
    
    log_success "GitHub Actions workflow configured"
}

# Create development workflow script
create_dev_workflow() {
    log_info "Creating development workflow script..."
    
    cat << 'EOF' > "$PROJECT_ROOT/dev_workflow.sh"
#!/bin/bash
# Development workflow automation

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_CHECKER="$PROJECT_ROOT/gradle_build_checker.sh"
BUILD_MONITOR="$PROJECT_ROOT/build_monitor.sh"

show_help() {
    echo "UTD Mod Development Workflow"
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  start     - Start development session"
    echo "  build     - Run full build check"
    echo "  quick     - Quick build check"
    echo "  monitor   - Start build monitor"
    echo "  status    - Show build status"
    echo "  clean     - Clean build artifacts"
    echo "  help      - Show this help"
}

start_dev_session() {
    echo "Starting UTD Mod development session..."
    
    # Run initial build check
    if [[ -f "$BUILD_CHECKER" ]]; then
        echo "Running initial build check..."
        "$BUILD_CHECKER" dev_session_start || true
    fi
    
    # Start file watcher in background
    if [[ "$MONITOR_CHANGES" == "true" ]] && [[ -f "$PROJECT_ROOT/file_watcher.sh" ]]; then
        echo "Starting file watcher..."
        "$PROJECT_ROOT/file_watcher.sh" &
        WATCHER_PID=$!
        echo "File watcher started (PID: $WATCHER_PID)"
        echo $WATCHER_PID > "$PROJECT_ROOT/.watcher.pid"
    fi
    
    echo "Development session started"
    echo "Use '$0 status' to check build status"
    echo "Use '$0 monitor' for detailed monitoring"
}

stop_dev_session() {
    echo "Stopping development session..."
    
    # Stop file watcher
    if [[ -f "$PROJECT_ROOT/.watcher.pid" ]]; then
        local watcher_pid=$(cat "$PROJECT_ROOT/.watcher.pid")
        if kill -0 "$watcher_pid" 2>/dev/null; then
            kill "$watcher_pid"
            echo "File watcher stopped"
        fi
        rm -f "$PROJECT_ROOT/.watcher.pid"
    fi
    
    echo "Development session stopped"
}

case "${1:-help}" in
    "start")
        start_dev_session
        ;;
    "stop")
        stop_dev_session
        ;;
    "build")
        "$BUILD_CHECKER" manual_trigger
        ;;
    "quick")
        "$BUILD_CHECKER" quick_check
        ;;
    "monitor")
        "$BUILD_MONITOR" once
        ;;
    "status")
        "$BUILD_MONITOR" status
        ;;
    "clean")
        ./gradlew clean
        ;;
    "help"|"-h"|"--help")
        show_help
        ;;
    *)
        echo "Unknown command: $1"
        show_help
        exit 1
        ;;
esac
EOF
    
    chmod +x "$PROJECT_ROOT/dev_workflow.sh"
    log_success "Development workflow script created"
}

# Main integration function
main() {
    local command="${1:-install}"
    
    echo "UTD Mod Build Integration Setup"
    echo "================================"
    
    case "$command" in
        "install")
            install_git_hooks
            setup_file_watcher
            setup_ide_integration
            setup_ci_integration
            create_dev_workflow
            log_success "Build integration setup complete"
            echo ""
            echo "Next steps:"
            echo "1. Run './dev_workflow.sh start' to begin development"
            echo "2. Use './gradle_build_checker.sh' for manual builds"
            echo "3. Use './build_monitor.sh' for status monitoring"
            ;;
        "git-hooks")
            install_git_hooks
            ;;
        "ide")
            setup_ide_integration
            ;;
        "ci")
            setup_ci_integration
            ;;
        "workflow")
            create_dev_workflow
            ;;
        "help"|"-h"|"--help")
            echo "Usage: $0 [command]"
            echo "Commands:"
            echo "  install    - Install all integrations (default)"
            echo "  git-hooks  - Install Git hooks only"
            echo "  ide        - Setup IDE integration only"
            echo "  ci         - Setup CI integration only"
            echo "  workflow   - Create workflow script only"
            echo "  help       - Show this help"
            ;;
        *)
            echo "Unknown command: $command"
            echo "Use '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
