#!/usr/bin/env python3
"""
Build Coordinator for Task Coordination
Monitors build results and provides JSON-based task coordination
"""

import json
import os
import sys
import time
import subprocess
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Any

class BuildCoordinator:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root)
        self.json_log_dir = self.project_root / "build_json"
        self.latest_summary = self.json_log_dir / "latest_build_summary.json"
        self.coordination_file = self.json_log_dir / "task_coordination.json"
        
        # Ensure directories exist
        self.json_log_dir.mkdir(exist_ok=True)
        
    def get_latest_build_result(self) -> Optional[Dict[str, Any]]:
        """Get the latest build result from JSON summary"""
        if not self.latest_summary.exists():
            return None
            
        try:
            with open(self.latest_summary, 'r') as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError) as e:
            print(f"Error reading build summary: {e}")
            return None
    
    def get_build_status(self) -> str:
        """Get current build status"""
        result = self.get_latest_build_result()
        if not result:
            return "no_build"
        
        return result.get("summary", {}).get("final_status", "unknown")
    
    def get_build_errors(self) -> List[Dict[str, Any]]:
        """Get list of build errors"""
        result = self.get_latest_build_result()
        if not result:
            return []
        
        return result.get("errors", [])
    
    def get_build_warnings_count(self) -> int:
        """Get count of build warnings"""
        result = self.get_latest_build_result()
        if not result:
            return 0
        
        return result.get("summary", {}).get("total_warnings", 0)
    
    def get_last_build_time(self) -> Optional[datetime]:
        """Get timestamp of last build"""
        result = self.get_latest_build_result()
        if not result:
            return None
        
        end_time_str = result.get("build_session", {}).get("end_time")
        if not end_time_str:
            return None
            
        try:
            return datetime.fromisoformat(end_time_str.replace('Z', '+00:00'))
        except ValueError:
            return None
    
    def is_build_needed(self, max_age_minutes: int = 30) -> bool:
        """Check if a new build is needed based on age and status"""
        last_build = self.get_last_build_time()
        if not last_build:
            return True
        
        # Check if build is too old
        age = datetime.now() - last_build
        if age > timedelta(minutes=max_age_minutes):
            return True
        
        # Check if last build failed
        status = self.get_build_status()
        return status in ["failed", "error", "unknown"]
    
    def trigger_build(self, trigger_reason: str = "coordinator_request") -> bool:
        """Trigger a new build using the gradle build checker"""
        print(f"Triggering build: {trigger_reason}")
        
        build_script = self.project_root / "gradle_build_checker.sh"
        if not build_script.exists():
            print(f"Build script not found: {build_script}")
            return False
        
        try:
            # Run build script
            result = subprocess.run(
                [str(build_script), trigger_reason],
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout
            )
            
            if result.returncode == 0:
                print("Build triggered successfully")
                return True
            else:
                print(f"Build failed with return code {result.returncode}")
                print(f"Error output: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            print("Build timed out after 5 minutes")
            return False
        except Exception as e:
            print(f"Error triggering build: {e}")
            return False
    
    def generate_task_coordination_report(self) -> Dict[str, Any]:
        """Generate task coordination report"""
        build_result = self.get_latest_build_result()
        build_status = self.get_build_status()
        errors = self.get_build_errors()
        warnings_count = self.get_build_warnings_count()
        last_build_time = self.get_last_build_time()
        
        coordination_data = {
            "timestamp": datetime.now().isoformat(),
            "project_root": str(self.project_root),
            "build_status": build_status,
            "build_needed": self.is_build_needed(),
            "last_build_time": last_build_time.isoformat() if last_build_time else None,
            "errors_count": len(errors),
            "warnings_count": warnings_count,
            "errors": errors,
            "recommendations": self._generate_recommendations(build_status, errors, warnings_count),
            "next_actions": self._generate_next_actions(build_status, errors)
        }
        
        # Save coordination data
        with open(self.coordination_file, 'w') as f:
            json.dump(coordination_data, f, indent=2)
        
        return coordination_data
    
    def _generate_recommendations(self, status: str, errors: List[Dict], warnings: int) -> List[str]:
        """Generate recommendations based on build status"""
        recommendations = []
        
        if status == "failed":
            recommendations.append("Fix build errors before proceeding")
            if errors:
                recommendations.append("Review compilation errors and fix syntax issues")
        elif status == "no_build":
            recommendations.append("Run initial build to establish baseline")
        elif warnings > 5:
            recommendations.append("Address build warnings to improve code quality")
        
        if status == "success":
            recommendations.append("Build is healthy - proceed with development")
        
        return recommendations
    
    def _generate_next_actions(self, status: str, errors: List[Dict]) -> List[str]:
        """Generate next actions based on build status"""
        actions = []
        
        if status in ["failed", "error", "unknown"]:
            actions.append("trigger_build")
            actions.append("review_errors")
        elif status == "no_build":
            actions.append("trigger_build")
        elif status == "success":
            actions.append("run_tests")
            actions.append("deploy_if_ready")
        
        return actions
    
    def print_coordination_report(self):
        """Print human-readable coordination report"""
        report = self.generate_task_coordination_report()
        
        print("=" * 50)
        print("BUILD COORDINATION REPORT")
        print("=" * 50)
        print(f"Timestamp: {report['timestamp']}")
        print(f"Project: {report['project_root']}")
        print(f"Build Status: {report['build_status']}")
        print(f"Build Needed: {report['build_needed']}")
        print(f"Last Build: {report['last_build_time']}")
        print(f"Errors: {report['errors_count']}")
        print(f"Warnings: {report['warnings_count']}")
        
        if report['recommendations']:
            print("\nRECOMMENDATIONS:")
            for rec in report['recommendations']:
                print(f"  • {rec}")
        
        if report['next_actions']:
            print("\nNEXT ACTIONS:")
            for action in report['next_actions']:
                print(f"  • {action}")
        
        if report['errors']:
            print("\nRECENT ERRORS:")
            for error in report['errors'][:3]:  # Show first 3 errors
                print(f"  • {error['component']}: {error['message']}")
        
        print("=" * 50)
    
    def watch_build_status(self, interval_seconds: int = 60):
        """Watch build status and trigger builds as needed"""
        print(f"Starting build status watcher (interval: {interval_seconds}s)")
        print("Press Ctrl+C to stop")
        
        try:
            while True:
                self.print_coordination_report()
                
                if self.is_build_needed():
                    print("\nBuild needed - triggering automatic build...")
                    self.trigger_build("automatic_watchdog")
                
                time.sleep(interval_seconds)
                
        except KeyboardInterrupt:
            print("\nBuild watcher stopped")

def main():
    if len(sys.argv) < 2:
        print("Usage: python build_coordinator.py <project_root> [command]")
        print("Commands:")
        print("  status     - Show current build status")
        print("  report     - Generate coordination report")
        print("  build      - Trigger new build")
        print("  watch      - Watch build status continuously")
        sys.exit(1)
    
    project_root = sys.argv[1]
    command = sys.argv[2] if len(sys.argv) > 2 else "status"
    
    coordinator = BuildCoordinator(project_root)
    
    if command == "status":
        status = coordinator.get_build_status()
        print(f"Build status: {status}")
        print(f"Build needed: {coordinator.is_build_needed()}")
        
    elif command == "report":
        coordinator.print_coordination_report()
        
    elif command == "build":
        success = coordinator.trigger_build("manual_request")
        sys.exit(0 if success else 1)
        
    elif command == "watch":
        interval = int(sys.argv[3]) if len(sys.argv) > 3 else 60
        coordinator.watch_build_status(interval)
        
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)

if __name__ == "__main__":
    main()
