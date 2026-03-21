#!/bin/bash

# GradleBuildCheck_Automation - Automated compilation checks with JSON error logging
# Author: WindSurf Penguin Alpha
# Purpose: Automate build checks after major changes and log results to JSON for task coordination

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
LOG_DIR="$PROJECT_ROOT/build_logs"
JSON_LOG_DIR="$PROJECT_ROOT/build_json"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BUILD_LOG="$LOG_DIR/gradle_build_$TIMESTAMP.log"
JSON_LOG="$JSON_LOG_DIR/build_result_$TIMESTAMP.json"
SUMMARY_LOG="$JSON_LOG_DIR/latest_build_summary.json"

# Ensure log directories exist
mkdir -p "$LOG_DIR"
mkdir -p "$JSON_LOG_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$BUILD_LOG"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$BUILD_LOG"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$BUILD_LOG"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$BUILD_LOG"
}

# JSON logging functions
create_json_entry() {
    local status="$1"
    local message="$2"
    local details="$3"
    local timestamp=$(date -Iseconds)
    
    cat <<EOF
{
  "timestamp": "$timestamp",
  "status": "$status",
  "message": "$message",
  "details": $details,
  "project_root": "$PROJECT_ROOT",
  "build_log": "$BUILD_LOG"
}
EOF
}

# Initialize JSON result
init_json_result() {
    local timestamp=$(date -Iseconds)
    cat <<EOF > "$JSON_LOG"
{
  "build_session": {
    "id": "$TIMESTAMP",
    "start_time": "$timestamp",
    "project_root": "$PROJECT_ROOT",
    "gradle_version": "$(./gradlew --version | head -1 | cut -d' ' -f2)",
    "java_version": "$(java -version 2>&1 | head -1 | cut -d'"' -f2)",
    "git_branch": "$(git branch --show-current 2>/dev/null || echo 'unknown')",
    "git_commit": "$(git rev-parse HEAD 2>/dev/null || echo 'unknown')",
    "trigger": "${BUILD_TRIGGER:-manual}"
  },
  "steps": [],
  "errors": [],
  "warnings": [],
  "summary": {
    "total_steps": 0,
    "successful_steps": 0,
    "failed_steps": 0,
    "total_errors": 0,
    "total_warnings": 0,
    "build_duration_ms": 0,
    "final_status": "running"
  }
}
EOF
}

# Update JSON result
update_json_step() {
    local step_name="$1"
    local status="$2"
    local message="$3"
    local details="$4"
    local duration_ms="$5"
    
    # Create temporary file for modification
    local temp_json=$(mktemp)
    
    # Add step to steps array
    jq --arg step_name "$step_name" \
       --arg status "$status" \
       --arg message "$message" \
       --arg details "$details" \
       --arg duration_ms "$duration_ms" \
       --arg timestamp "$(date -Iseconds)" \
       '.steps += [{
         "name": $step_name,
         "status": $status,
         "message": $message,
         "details": $details,
         "duration_ms": ($duration_ms | tonumber),
         "timestamp": $timestamp
       }]' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
    
    # Update summary
    if [[ "$status" == "success" ]]; then
        jq '.summary.successful_steps += 1' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
    elif [[ "$status" == "error" ]]; then
        jq '.summary.failed_steps += 1 | .summary.total_errors += 1' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
    elif [[ "$status" == "warning" ]]; then
        jq '.summary.total_warnings += 1' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
    fi
    
    jq '.summary.total_steps += 1' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
}

# Add error to JSON
add_json_error() {
    local component="$1"
    local error_message="$2"
    local error_details="$3"
    
    local temp_json=$(mktemp)
    jq --arg component "$component" \
       --arg message "$error_message" \
       --arg details "$error_details" \
       --arg timestamp "$(date -Iseconds)" \
       '.errors += [{
         "component": $component,
         "message": $message,
         "details": $details,
         "timestamp": $timestamp
       }]' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
}

# Finalize JSON result
finalize_json_result() {
    local final_status="$1"
    local total_duration="$2"
    
    local temp_json=$(mktemp)
    jq --arg status "$final_status" \
       --arg duration "$total_duration" \
       --arg end_time "$(date -Iseconds)" \
       '.summary.final_status = $status |
        .summary.build_duration_ms = ($duration | tonumber) |
        .build_session.end_time = $end_time' "$JSON_LOG" > "$temp_json" && mv "$temp_json" "$JSON_LOG"
    
    # Create summary file
    cp "$JSON_LOG" "$SUMMARY_LOG"
}

# Build step execution with timing
execute_build_step() {
    local step_name="$1"
    local step_command="$2"
    local step_description="$3"
    
    log_info "Executing step: $step_name"
    log_info "Description: $step_description"
    
    local start_time=$(date +%s%3N)
    local temp_output=$(mktemp)
    
    # Execute command and capture output
    if eval "$step_command" > "$temp_output" 2>&1; then
        local end_time=$(date +%s%3N)
        local duration=$((end_time - start_time))
        
        log_success "Step '$step_name' completed successfully in ${duration}ms"
        update_json_step "$step_name" "success" "Step completed successfully" "" "$duration"
        
        # Check for warnings in output
        if grep -i "warning" "$temp_output" >/dev/null; then
            local warnings=$(grep -i "warning" "$temp_output" | wc -l)
            log_warning "Found $warnings warnings in step output"
            update_json_step "$step_name" "warning" "Step completed with $warnings warnings" "" "$duration"
        fi
        
        return 0
    else
        local end_time=$(date +%s%3N)
        local duration=$((end_time - start_time))
        local exit_code=$?
        local error_output=$(cat "$temp_output" | head -20)
        
        log_error "Step '$step_name' failed with exit code $exit_code after ${duration}ms"
        log_error "Error output: $error_output"
        
        update_json_step "$step_name" "error" "Step failed with exit code $exit_code" "$error_output" "$duration"
        add_json_error "$step_name" "Step failed with exit code $exit_code" "$error_output"
        
        return $exit_code
    fi
}

# Check for major changes
detect_major_changes() {
    log_info "Detecting major changes since last build..."
    
    # Get last build commit if available
    local last_build_file="$JSON_LOG_DIR/last_build_commit.txt"
    local last_commit=""
    
    if [[ -f "$last_build_file" ]]; then
        last_commit=$(cat "$last_build_file")
    fi
    
    # Check for changes
    local changed_files=""
    if [[ -n "$last_commit" ]]; then
        changed_files=$(git diff --name-only "$last_commit" HEAD 2>/dev/null || echo "")
    else
        changed_files=$(git ls-files 2>/dev/null || echo "")
    fi
    
    # Check for major file changes
    local major_changes=()
    local java_changes=()
    local resource_changes=()
    
    while IFS= read -r file; do
        if [[ -n "$file" ]]; then
            if [[ "$file" == *.java ]]; then
                java_changes+=("$file")
            elif [[ "$file" == src/main/resources/* ]]; then
                resource_changes+=("$file")
            fi
            
            # Check for critical files
            if [[ "$file" == "build.gradle" ]] || [[ "$file" == "gradle.properties" ]]; then
                major_changes+=("CRITICAL: $file")
            elif [[ "$file" == src/main/java/com/utdmod/* ]]; then
                major_changes+=("MAJOR: $file")
            fi
        fi
    done <<< "$changed_files"
    
    # Log changes
    if [[ ${#major_changes[@]} -gt 0 ]]; then
        log_warning "Major changes detected:"
        for change in "${major_changes[@]}"; do
            log_warning "  $change"
        done
    fi
    
    if [[ ${#java_changes[@]} -gt 0 ]]; then
        log_info "Java files changed: ${#java_changes[@]}"
    fi
    
    if [[ ${#resource_changes[@]} -gt 0 ]]; then
        log_info "Resource files changed: ${#resource_changes[@]}"
    fi
    
    # Store current commit for next build
    git rev-parse HEAD > "$last_build_file" 2>/dev/null || true
}

# Main build check function
run_build_check() {
    local overall_start_time=$(date +%s%3N)
    
    log_info "Starting Gradle build check automation"
    log_info "Build session ID: $TIMESTAMP"
    log_info "Project root: $PROJECT_ROOT"
    
    # Initialize JSON logging
    init_json_result
    
    # Detect changes
    detect_major_changes
    
    # Build steps
    local steps=(
        "clean:./gradlew clean:Clean previous build artifacts"
        "compile_java:./gradlew compileJava:Compile Java source code"
        "process_resources:./gradlew processResources:Process resource files"
        "classes:./gradlew classes:Assemble compiled classes"
        "jar:./gradlew jar:Build JAR file"
        "build:./gradlew build:Run full build including tests"
    )
    
    local build_failed=false
    
    for step in "${steps[@]}"; do
        IFS=':' read -r step_name step_command step_description <<< "$step"
        
        if ! execute_build_step "$step_name" "$step_command" "$step_description"; then
            build_failed=true
            log_error "Build failed at step: $step_name"
            break
        fi
        
        log_info "Step output:" | tee -a "$BUILD_LOG"
        echo "---" | tee -a "$BUILD_LOG"
    done
    
    # Calculate total duration
    local overall_end_time=$(date +%s%3N)
    local total_duration=$((overall_end_time - overall_start_time))
    
    # Finalize result
    local final_status="success"
    if [[ "$build_failed" == "true" ]]; then
        final_status="failed"
        log_error "Build check FAILED after ${total_duration}ms"
    else
        log_success "Build check PASSED after ${total_duration}ms"
    fi
    
    finalize_json_result "$final_status" "$total_duration"
    
    # Generate summary report
    generate_summary_report
    
    log_info "Build check completed"
    log_info "Detailed log: $BUILD_LOG"
    log_info "JSON result: $JSON_LOG"
    log_info "Summary: $SUMMARY_LOG"
    
    return $([[ "$build_failed" == "true" ]] && echo 1 || echo 0)
}

# Generate summary report
generate_summary_report() {
    local report_file="$JSON_LOG_DIR/build_report_$TIMESTAMP.txt"
    
    cat <<EOF > "$report_file"
=== GRADLE BUILD CHECK REPORT ===
Session ID: $TIMESTAMP
Timestamp: $(date)
Project: $PROJECT_ROOT

BUILD SUMMARY:
Status: $(jq -r '.summary.final_status' "$JSON_LOG")
Duration: $(jq -r '.summary.build_duration_ms' "$JSON_LOG")ms
Total Steps: $(jq -r '.summary.total_steps' "$JSON_LOG")
Successful: $(jq -r '.summary.successful_steps' "$JSON_LOG")
Failed: $(jq -r '.summary.failed_steps' "$JSON_LOG")
Errors: $(jq -r '.summary.total_errors' "$JSON_LOG")
Warnings: $(jq -r '.summary.total_warnings' "$JSON_LOG")

STEP DETAILS:
$(jq -r '.steps[] | "- \(.name): \(.status) (\(.duration_ms)ms)"' "$JSON_LOG")

ERRORS:
$(jq -r '.errors[] | "- \(.component): \(.message)"' "$JSON_LOG")

FILES:
Build Log: $BUILD_LOG
JSON Log: $JSON_LOG
Summary: $SUMMARY_LOG

EOF

    log_info "Summary report generated: $report_file"
}

# Main execution
main() {
    # Set build trigger if provided
    BUILD_TRIGGER="${1:-manual}"
    
    # Change to project directory
    cd "$PROJECT_ROOT"
    
    # Run build check
    if run_build_check; then
        log_success "Gradle build check automation completed successfully"
        exit 0
    else
        log_error "Gradle build check automation failed"
        exit 1
    fi
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
