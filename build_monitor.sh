#!/bin/bash

# Build Monitor - Continuous build status monitoring and notification system
# Integrates with build_coordinator.py for automated build checks

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
PYTHON_SCRIPT="$PROJECT_ROOT/build_coordinator.py"

# Configuration
MONITOR_INTERVAL=${MONITOR_INTERVAL:-60}  # seconds
LOG_FILE="$PROJECT_ROOT/build_json/build_monitor.log"
NOTIFICATION_ENABLED=${NOTIFICATION_ENABLED:-true}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_message() {
    local level="$1"
    local message="$2"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
    log_message "INFO" "$1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
    log_message "SUCCESS" "$1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
    log_message "WARNING" "$1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
    log_message "ERROR" "$1"
}

# Check dependencies
check_dependencies() {
    if ! command -v python3 &> /dev/null; then
        log_error "Python3 is required but not installed"
        exit 1
    fi
    
    if ! [[ -f "$PYTHON_SCRIPT" ]]; then
        log_error "Build coordinator script not found: $PYTHON_SCRIPT"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_warning "jq is recommended for JSON processing"
    fi
}

# Send notification (placeholder for different notification systems)
send_notification() {
    local title="$1"
    local message="$2"
    local priority="${3:-normal}"
    
    if [[ "$NOTIFICATION_ENABLED" != "true" ]]; then
        return 0
    fi
    
    # Desktop notification (if available)
    if command -v notify-send &> /dev/null; then
        notify-send "$title" "$message" -u "$priority" 2>/dev/null || true
    fi
    
    # Log notification
    log_message "NOTIFICATION" "$title: $message"
}

# Monitor build status
monitor_build_status() {
    local last_status=""
    local consecutive_failures=0
    local max_failures=3
    
    log_info "Starting build status monitoring (interval: ${MONITOR_INTERVAL}s)"
    log_info "Press Ctrl+C to stop monitoring"
    
    while true; do
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        
        # Get build status using Python coordinator
        local build_status
        build_status=$(python3 "$PYTHON_SCRIPT" "$PROJECT_ROOT" status 2>/dev/null | grep "Build status:" | cut -d' ' -f3 || echo "unknown")
        
        # Get build needed status
        local build_needed
        build_needed=$(python3 "$PYTHON_SCRIPT" "$PROJECT_ROOT" status 2>/dev/null | grep "Build needed:" | cut -d' ' -f3 || echo "unknown")
        
        log_info "Build Status: $build_status | Build Needed: $build_needed"
        
        # Check for status changes
        if [[ "$last_status" != "$build_status" ]]; then
            if [[ "$build_status" == "success" ]]; then
                log_success "Build status changed to SUCCESS"
                send_notification "Build Success" "UTD Mod build completed successfully" "normal"
                consecutive_failures=0
            elif [[ "$build_status" == "failed" ]]; then
                log_error "Build status changed to FAILED"
                send_notification "Build Failed" "UTD Mod build has failed" "critical"
                ((consecutive_failures++))
            elif [[ "$build_status" == "no_build" ]]; then
                log_warning "No previous build found"
                send_notification "No Build" "No previous build found - triggering initial build" "normal"
            fi
            
            last_status="$build_status"
        fi
        
        # Handle consecutive failures
        if [[ $consecutive_failures -ge $max_failures ]]; then
            log_error "Too many consecutive build failures ($consecutive_failures)"
            send_notification "Build Alert" "Multiple consecutive build failures detected" "critical"
            # Reset counter to avoid spam
            consecutive_failures=0
        fi
        
        # Trigger build if needed
        if [[ "$build_needed" == "True" ]]; then
            log_info "Build needed - triggering automatic build"
            if python3 "$PYTHON_SCRIPT" "$PROJECT_ROOT" build >/dev/null 2>&1; then
                log_success "Build triggered successfully"
                send_notification "Build Triggered" "Automatic build triggered due to changes" "normal"
            else
                log_error "Failed to trigger build"
            fi
        fi
        
        # Wait for next check
        sleep "$MONITOR_INTERVAL"
    done
}

# Generate detailed status report
generate_status_report() {
    log_info "Generating detailed build status report..."
    
    if python3 "$PYTHON_SCRIPT" "$PROJECT_ROOT" report; then
        log_success "Status report generated successfully"
        
        # Show summary in log
        local summary_file="$PROJECT_ROOT/build_json/task_coordination.json"
        if [[ -f "$summary_file" ]]; then
            local status=$(jq -r '.build_status' "$summary_file" 2>/dev/null || echo "unknown")
            local errors=$(jq -r '.errors_count' "$summary_file" 2>/dev/null || echo "0")
            local warnings=$(jq -r '.warnings_count' "$summary_file" 2>/dev/null || echo "0")
            
            log_info "Report Summary: Status=$status, Errors=$errors, Warnings=$warnings"
        fi
    else
        log_error "Failed to generate status report"
    fi
}

# Quick status check
quick_status_check() {
    local status
    status=$(python3 "$PYTHON_SCRIPT" "$PROJECT_ROOT" status 2>/dev/null || echo "unknown")
    
    echo "$status"
    
    # Return appropriate exit code
    case "$status" in
        "success") return 0 ;;
        "failed") return 1 ;;
        "no_build") return 2 ;;
        *) return 3 ;;
    esac
}

# Main function
main() {
    local command="${1:-monitor}"
    
    # Ensure log directory exists
    mkdir -p "$(dirname "$LOG_FILE")"
    
    # Check dependencies
    check_dependencies
    
    case "$command" in
        "monitor")
            monitor_build_status
            ;;
        "status")
            quick_status_check
            ;;
        "report")
            generate_status_report
            ;;
        "once")
            generate_status_report
            ;;
        "help"|"-h"|"--help")
            echo "Usage: $0 [command]"
            echo "Commands:"
            echo "  monitor    - Continuous monitoring (default)"
            echo "  status     - Quick status check"
            echo "  report     - Generate detailed report"
            echo "  once       - Generate report once and exit"
            echo "  help       - Show this help"
            ;;
        *)
            log_error "Unknown command: $command"
            echo "Use '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
