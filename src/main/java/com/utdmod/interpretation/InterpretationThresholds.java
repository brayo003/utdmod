package com.utdmod.interpretation;

/**
 * Read-only interpretation thresholds derived from laboratory SubstrateSnapshotInterpreter.
 * These tune semantic classification only — they do not modify substrate dynamics.
 */
public final class InterpretationThresholds {
  /** Minimum sustained activation for persistent ecological behavior. */
  public static final double ACTIVATION_PERSIST = 0.010;
  /** Minimum memory for scar / prosperity classification. */
  public static final double MEMORY_PERSIST = 0.050;
  /** High readiness boundary for danger / storm semantics. */
  public static final double READINESS_HIGH = 0.60;
  /** Transport level indicating a travel corridor. */
  public static final double TRANSPORT_CORRIDOR = 0.008;
  /** Field depth indicating settled structure. */
  public static final double FIELD_DEPTH_SETTLED = 0.020;
  /** Neighbor activation density for settlement clustering. */
  public static final double LOCAL_DENSITY_SETTLE = 0.15;
  /** Activation gradient indicating frontier pressure. */
  public static final double GRADIENT_FRONTIER = 0.003;
  /** Ticks of evidence before high-confidence intents emit. */
  public static final int PERSISTENCE_TICKS = 3;

  private InterpretationThresholds() {}
}
