package com.utdmod.interpretation;

/**
 * Semantic description of substrate state at a chunk.
 * All values are read-only interpretations — never written back to the field.
 */
public final class SubstrateSemantics {
  public final double ecologicalStress;
  public final double environmentalStability;
  public final double migrationPressure;
  public final double corridorStrength;
  public final double territorialPressure;
  public final double recoveryState;
  public final double prosperityPotential;
  public final double explorationTendency;
  public final double conflictTendency;
  public final double settlementTendency;
  public final double roadFormationTendency;
  public final double tradeTendency;
  public final double expansionTendency;
  public final double collapseTendency;

  public final double localGradient;
  public final double localDensity;
  public final double avgNeighborTransport;
  public final double transportBiasX;
  public final double transportBiasZ;

  public SubstrateSemantics(
      double ecologicalStress, double environmentalStability, double migrationPressure,
      double corridorStrength, double territorialPressure, double recoveryState,
      double prosperityPotential, double explorationTendency, double conflictTendency,
      double settlementTendency, double roadFormationTendency, double tradeTendency,
      double expansionTendency, double collapseTendency,
      double localGradient, double localDensity, double avgNeighborTransport,
      double transportBiasX, double transportBiasZ) {
    this.ecologicalStress = ecologicalStress;
    this.environmentalStability = environmentalStability;
    this.migrationPressure = migrationPressure;
    this.corridorStrength = corridorStrength;
    this.territorialPressure = territorialPressure;
    this.recoveryState = recoveryState;
    this.prosperityPotential = prosperityPotential;
    this.explorationTendency = explorationTendency;
    this.conflictTendency = conflictTendency;
    this.settlementTendency = settlementTendency;
    this.roadFormationTendency = roadFormationTendency;
    this.tradeTendency = tradeTendency;
    this.expansionTendency = expansionTendency;
    this.collapseTendency = collapseTendency;
    this.localGradient = localGradient;
    this.localDensity = localDensity;
    this.avgNeighborTransport = avgNeighborTransport;
    this.transportBiasX = transportBiasX;
    this.transportBiasZ = transportBiasZ;
  }
}
