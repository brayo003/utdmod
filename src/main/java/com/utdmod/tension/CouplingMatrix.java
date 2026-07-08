package com.utdmod.tension;

/**
 * Explicit coupling matrix: how each state variable influences the derivatives of others.
 * Indices: 0=A, 1=M, 2=D, 3=B
 */
public class CouplingMatrix {
    private final double[][] coupling = new double[4][4];

    public CouplingMatrix() {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                coupling[i][j] = 0.0;
            }
        }
    }

    public void set(int affectedState, int sourceState, double strength) {
        if (affectedState >= 0 && affectedState < 4 && sourceState >= 0 && sourceState < 4) {
            coupling[affectedState][sourceState] = strength;
        }
    }

    public double get(int affectedState, int sourceState) {
        if (affectedState >= 0 && affectedState < 4 && sourceState >= 0 && sourceState < 4) {
            return coupling[affectedState][sourceState];
        }
        return 0.0;
    }

    public double[] applyToState(double[] states) {
        double[] result = new double[4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result[i] += coupling[i][j] * states[j];
            }
        }
        return result;
    }

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
