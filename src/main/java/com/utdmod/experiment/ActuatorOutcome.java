package com.utdmod.experiment;

/**
 * Tracks the actual outcome of an actuator execution.
 * Distinguishes between "actuator ran" and "actuator changed something".
 */
public enum ActuatorOutcome {
    /** Actuator executed and made changes. */
    SUCCESS,
    /** Actuator executed but made no changes (already in desired state). */
    NO_CHANGE,
    /** Actuator could not find a target (e.g., no crops to grow). */
    NO_TARGET,
    /** Actuator execution failed due to validation or error. */
    FAILED,
    /** Block/entity already in correct state. */
    ALREADY_PRESENT,
    /** Threshold not met; actuator did not execute. */
    THRESHOLD_NOT_MET,
    /** Unknown or unspecified outcome. */
    UNKNOWN
}
