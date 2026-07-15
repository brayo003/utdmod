import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

public class ProbeMemoryLaw {
    public static void main(String[] args) throws Exception {
        // produce three protocols
        runProtocol("protocol1_single_impulse", 500, 0, 1, 0.0, 1);
        runProtocol("protocol2_continuous_100_then_1000", 1100, 100, 1000, 0.2, 1);
        runProtocol("protocol3_continuous_10000_then_5000", 15000, 10000, 5000, 0.2, 1);
    }

    static void runProtocol(String name, int totalTicks, int stimTicks, int quietTicks, double stimStrength, int runs) throws IOException {
        String out = "/tmp/" + name + ".csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("tick,A,M,B,D,formation,saturation,forgetting,antiDiffusion,diffusion,netMemoryDerivative,activationDrive,baselineDrive,normalizedDrive,depthModulation,retention,effectiveDecayRate");

            ParameterSet params = new ParameterSet();
            GridSimulationV2 sim = new GridSimulationV2(1, 1, totalTicks, params);
            // initialize baseline to resting
            sim.baseline[0][0] = params.v2BaselineResting;
            sim.activation[0][0] = 0.0;
            sim.memory[0][0] = 0.0;
            sim.fieldDepth[0][0] = 0.0;

            for (int t = 0; t < totalTicks; t++) {
                double external = 0.0;
                if (t < stimTicks) external = stimStrength;
                // compute stencil
                SpatialStencil stencil = new SpatialStencil(0, 0, sim.activation, sim.memory, sim.fieldDepth, sim.baseline);
                double A = sim.activation[0][0];
                double M = sim.memory[0][0];
                double B = sim.baseline[0][0];
                double D = sim.fieldDepth[0][0];

                // compute formation components matching the updated GridSimulationV2 logic
                double activationDrive = Math.max(0.0, A - params.memoryFormationThreshold);
                if (activationDrive <= 0.0) {
                    activationDrive = 0.0;
                }
                double baselineDrive = 0.0;
                double drive = activationDrive;
                double normalizedDrive = drive / (1.0 + drive);
                double depthModulation = 1.0 + params.memoryDepthRetentionGain * Math.min(1.0, D / (1.0 + D));
                double formationBase = params.memoryFormationGain * normalizedDrive * depthModulation;
                double activationPeakStrength = Math.max(0.0, -stencil.activationLaplacian) / (0.1 + Math.abs(stencil.activationLaplacian));
                double formation = formationBase * (0.30 + 0.70 * activationPeakStrength);
                double saturation = (1.0 - (M / Math.max(1e-6, params.memoryMaximum))) * formation;

                double retention = 1.0 / (1.0 + params.memoryDepthRetentionGain * Math.max(0.0, D));
                double effectiveDecayRate = params.memoryDecayRate * retention;
                double activityForgettingFactor = 2.0 + 3.0 * (1.0 - Math.min(1.0, Math.max(0.0, A)));
                double forgetting = -0.4 * effectiveDecayRate * M * activityForgettingFactor;

                double antiDiffusion = -0.15 * params.memoryFormationGain * stencil.memoryLaplacian;
                double diffusion = 0.25 * params.memoryDiffusionGain * stencil.memoryLaplacian;
                double netdM = saturation + forgetting + antiDiffusion + diffusion;

                pw.printf(java.util.Locale.ROOT, "%d,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f\n",
                        t, A, M, B, D, formation, saturation, forgetting, antiDiffusion, diffusion, netdM,
                        activationDrive, baselineDrive, normalizedDrive, depthModulation, retention, effectiveDecayRate);

                // advance simulation state using the implementation's update to ensure fidelity
                GridSimulationV2.CellUpdateResult res = sim.integrateUnifiedCellStep(0, 0, external, 0.0);
                sim.activation[0][0] = res.nextActivation;
                sim.memory[0][0] = res.nextMemory;
                sim.baseline[0][0] = res.nextBaseline;
                sim.fieldDepth[0][0] = res.nextDepth;
            }
        }
        System.out.println("Wrote " + out);
    }
}
