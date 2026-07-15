/**
 * Unified field derivative representation.
 * Computed synchronously from identical local state.
 * All four state variables have their time derivatives computed in parallel.
 */
public class FieldDerivative {
    public double dActivation;
    public double dMemory;
    public double dDepth;
    public double dBaseline;

    public FieldDerivative() {
        this.dActivation = 0.0;
        this.dMemory = 0.0;
        this.dDepth = 0.0;
        this.dBaseline = 0.0;
    }

    public FieldDerivative(double dA, double dM, double dD, double dB) {
        this.dActivation = dA;
        this.dMemory = dM;
        this.dDepth = dD;
        this.dBaseline = dB;
    }

    @Override
    public String toString() {
        return String.format("dA=%.6f, dM=%.6f, dD=%.6f, dB=%.6f",
                dActivation, dMemory, dDepth, dBaseline);
    }
}
