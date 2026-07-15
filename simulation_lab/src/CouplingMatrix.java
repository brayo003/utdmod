/**
 * Explicit coupling matrix: how each state variable influences the derivatives of others.
 * 
 * Structure:
 *   affects  →    A      M      D      B
 *   ↓
 *   A     [AA     AM     AD     AB]     (influences dA)
 *   M     [MA     MM     MD     MB]     (influences dM)
 *   D     [DA     DM     DD     DB]     (influences dD)
 *   B     [BA     BM     BD     BB]     (influences dB)
 *
 * Entry C[i][j] is the coupling strength: how state variable j affects the derivative of state i.
 * Dimensionless, typically in range [-0.1, +0.1].
 *
 * Indices: 0=A, 1=M, 2=D, 3=B
 */
public class CouplingMatrix {
    // 4x4 matrix: coupling[affected_state][source_state]
    private double[][] coupling;

    public CouplingMatrix() {
        this.coupling = new double[4][4];
        // Initialize with zeros
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                coupling[i][j] = 0.0;
            }
        }
    }

    /**
     * Set coupling strength: how state j affects derivative of state i.
     * @param affectedState 0=A, 1=M, 2=D, 3=B (which derivative is affected)
     * @param sourceState   0=A, 1=M, 2=D, 3=B (which state affects it)
     * @param strength      coupling coefficient
     */
    public void set(int affectedState, int sourceState, double strength) {
        if (affectedState >= 0 && affectedState < 4 && sourceState >= 0 && sourceState < 4) {
            coupling[affectedState][sourceState] = strength;
        }
    }

    /**
     * Get coupling strength.
     */
    public double get(int affectedState, int sourceState) {
        if (affectedState >= 0 && affectedState < 4 && sourceState >= 0 && sourceState < 4) {
            return coupling[affectedState][sourceState];
        }
        return 0.0;
    }

    /**
     * Compute derivative from coupling: C·S where S = [A, M, D, B]
     * Returns the contribution of each state variable to each derivative.
     * @param states [A, M, D, B]
     * @return [dA_coupling, dM_coupling, dD_coupling, dB_coupling]
     */
    public double[] applyToState(double[] states) {
        double[] result = new double[4];
        for (int i = 0; i < 4; i++) {
            result[i] = 0.0;
            for (int j = 0; j < 4; j++) {
                result[i] += coupling[i][j] * states[j];
            }
        }
        return result;
    }

    /**
     * Print matrix for debugging.
     */
    @Override
    public String toString() {
        String[] labels = {"A", "M", "D", "B"};
        StringBuilder sb = new StringBuilder("Coupling Matrix:\n");
        sb.append("       ");
        for (int j = 0; j < 4; j++) {
            sb.append(String.format("%8s ", labels[j]));
        }
        sb.append("\n");
        for (int i = 0; i < 4; i++) {
            sb.append(labels[i]).append("    ");
            for (int j = 0; j < 4; j++) {
                sb.append(String.format("%8.4f ", coupling[i][j]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
