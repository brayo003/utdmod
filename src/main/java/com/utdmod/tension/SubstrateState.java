package com.utdmod.tension;

/**
 * Substrate state: activation, memory, transport, field depth, baseline.
 * This is the only evolving dynamical field.
 *
 * Local tension is NOT stored here. It is computed on demand via computeLocalTension().
 */
public class SubstrateState {
    private double activation;
    private double memory;
    private double transport;
    private double fieldDepth;
    private double baseline;
    private double externalInput;
    private double readiness;
    private double radius;
    private double capacity;
    private double peakActivation;
    private double convergence;

    /**
     * Threshold above which a chunk is considered "active" for region formation
     * and active-fraction metrics.
     */
    public static final double ACTIVE_TENSION_THRESHOLD = 0.15;

    public SubstrateState() {
        this(0.0, 0.0, 0.0, 0.0);
    }

    public SubstrateState(double activation, double memory, double transport, double fieldDepth) {
        this(activation, memory, transport, fieldDepth, 0.0);
    }

    public SubstrateState(double activation, double memory, double transport,
                          double fieldDepth, double baseline) {
        this.activation = activation;
        this.memory = memory;
        this.transport = transport;
        this.fieldDepth = fieldDepth;
        this.baseline = baseline;
        this.externalInput = 0.0;
        refreshDerivedObservables(ParameterSet.runtime());
    }

    // ---- Deprecated integration paths ----

    @Deprecated
    public void updateFromLocalTension(double localTension, double neighborAvg,
                                       double globalTension, boolean stormActive,
                                       double externalInput) {
        throw new UnsupportedOperationException(
            "Deprecated integration path. Use ChunkTensionData.integrateAllSubstrateStates.");
    }

    @Deprecated
    public void updateWithTransport(double externalInput, double incomingTransport) {
        throw new UnsupportedOperationException(
            "Deprecated integration path. Use ChunkTensionData.integrateAllSubstrateStates.");
    }

    @Deprecated
    public void advance(double externalInput, double incomingTransport) {
        throw new UnsupportedOperationException(
            "Deprecated integration path. Use ChunkTensionData.integrateAllSubstrateStates.");
    }

    // ---- Integration ----

    /** Immutable copy for synchronous gather-phase reads. */
    public SubstrateState snapshotCopy() {
        SubstrateState copy = new SubstrateState();
        copy.restoreFromPersistence(
            activation, memory, transport, fieldDepth, baseline, externalInput,
            readiness, radius, capacity, peakActivation, convergence);
        return copy;
    }

    /** Integrate on a copy without mutating this instance (apply-phase). */
    public SubstrateState integrateCopy(double externalInput, double incomingTransport,
                                        SpatialStencil stencil, ParameterSet parameters) {
        SubstrateState next = snapshotCopy();
        next.applyUnifiedFieldStep(externalInput, incomingTransport, stencil, parameters);
        return next;
    }

    /** Euler integration step using the canonical unified field derivative. */
    public void applyUnifiedFieldStep(double externalInput, double incomingTransport,
                                      SpatialStencil stencil, ParameterSet parameters) {
        double prevA = activation;
        double prevM = memory;
        double prevD = fieldDepth;
        double prevB = baseline;

        FieldDerivative deriv = FieldDerivative.computeUnified(
            prevA, prevM, prevD, prevB,
            stencil, externalInput, incomingTransport, parameters);

        activation  = clamp(prevA + deriv.dActivation, 0.0, parameters.v2ClampMax);
        memory      = clamp(prevM + deriv.dMemory,     0.0, parameters.v2ClampMax);
        fieldDepth  = clamp(prevD + deriv.dDepth,      0.0, parameters.v2ClampMax);
        baseline    = clamp(prevB + deriv.dBaseline,   0.0, parameters.v2ClampMax);
        transport   = Math.max(0.0, incomingTransport);
        this.externalInput = externalInput;

        peakActivation = Math.max(peakActivation, activation);
        convergence = 1.0 / (1.0 + Math.abs(activation - prevA) + memory + fieldDepth);
        refreshDerivedObservables(parameters);
    }

    private void refreshDerivedObservables(ParameterSet parameters) {
        readiness = computeReadiness(memory, fieldDepth, parameters);
        radius    = SubstrateTransport.computeTransportRadius(fieldDepth, parameters);
        capacity  = computeDestinationCapacity(activation);
    }

    public void restoreFromPersistence(double activation, double memory, double transport,
                                       double fieldDepth, double baseline, double externalInput,
                                       double readiness, double radius, double capacity,
                                       double peakActivation, double convergence) {
        this.activation     = activation;
        this.memory         = memory;
        this.transport      = transport;
        this.fieldDepth     = fieldDepth;
        this.baseline       = baseline;
        this.externalInput  = externalInput;
        this.readiness      = readiness;
        this.radius         = radius;
        this.capacity       = capacity;
        this.peakActivation = peakActivation;
        this.convergence    = convergence;
    }

    // ---- Derived tension metric ----

    /**
     * T(x) = wA*A' + wM*M' + wD*D', normalized by sum of active weights.
     * Delegates to SubstrateObservationModel — kept for call-site convenience.
     */
    public double computeLocalTension() {
        return SubstrateObservationModel.computeLocalTension(this);
    }

    // ---- Static helpers ----

    public static double computeTransportContribution(double sourceActivation, double sourceMemory,
                                                      double destinationActivation, double distance,
                                                      ParameterSet parameters) {
        double neighborStim = sourceActivation / (1.0 + sourceActivation);
        neighborStim *= (1.0 + parameters.v2MemoryInfluence
            * (sourceMemory / (sourceMemory + parameters.v2MemoryEffectK)));
        double weight = parameters.v3DistanceDecay > 0.0
            ? Math.exp(-distance / parameters.v3DistanceDecay)
            : 1.0 / (1.0 + distance);
        double t = neighborStim * weight * parameters.v2TransportGain;
        t *= (1.0 - destinationActivation / (1.0 + destinationActivation));
        return t;
    }

    public static double computeReadiness(double memoryValue, double fieldDepthValue,
                                          ParameterSet parameters) {
        double memoryScale = Math.max(0.5, parameters.readinessMemoryScale);
        return Math.exp(-Math.max(0.0, memoryValue) / memoryScale);
    }

    public static double computeDestinationCapacity(double activationValue) {
        return 1.0 / (1.0 + 0.5 * Math.max(0.0, activationValue));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- Accessors ----

    public void setBaseline(double baseline) { this.baseline = baseline; }

    public double getActivation()     { return activation; }
    public double getMemory()         { return memory; }
    public double getTransport()      { return transport; }
    public double getFieldDepth()     { return fieldDepth; }
    public double getBaseline()       { return baseline; }
    public double getExternalInput()  { return externalInput; }
    public double getReadiness()      { return readiness; }
    public double getRadius()         { return radius; }
    public double getCapacity()       { return capacity; }
    public double getPeakActivation() { return peakActivation; }
    public double getConvergence()    { return convergence; }
}
