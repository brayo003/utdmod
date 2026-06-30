import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GameplayValidationInstrumentationTest {

    @Test
    void writesRequestedInstrumentationFiles() throws Exception {
        File dir = Files.createTempDirectory("gameplay-instrumentation").toFile();
        GameplayValidationExperiments.DiagnosticsManager diagnostics = new GameplayValidationExperiments.DiagnosticsManager(dir);

        ParameterSet parameters = new ParameterSet();
        parameters.v2TransportGain = 0.1;
        parameters.v2ActivationDecay = 0.2;
        parameters.v2MemoryGain = 0.05;
        parameters.v2MemoryDecay = 0.01;
        parameters.v2MemoryInfluence = 1.0;
        parameters.v2ClampMax = 2.0;
        parameters.v3FieldDepthGain = 0.01;
        parameters.v3FieldDepthDecay = 0.001;
        parameters.v3FieldDepthRadius = 2.0;
        parameters.v3FieldDepthEffectK = 0.1;
        parameters.v3DistanceDecay = 1.0;
        parameters.validationMaxHotspotRadius = 4.0;

        double[][] activation = new double[][]{{0.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 0.0}};
        double[][] memory = new double[][]{{0.0, 0.0, 0.0}, {0.0, 0.2, 0.0}, {0.0, 0.0, 0.0}};
        double[][] fieldDepth = new double[][]{{0.0, 0.0, 0.0}, {0.0, 0.1, 0.0}, {0.0, 0.0, 0.0}};

        diagnostics.recordTick(0, activation, memory, fieldDepth);
        diagnostics.writeRuntimeParameters("demo", parameters, 3, 3, 5, 2, 1);
        diagnostics.writeExperimentSummary("demo", parameters, 3, 3, 5, 2, 1, activation, memory, fieldDepth, "probe", "completed", "none", true, true, true, true, true);
        diagnostics.writeFinalGrids(activation, memory, fieldDepth);
        diagnostics.close();

        assertTrue(new File(dir, "parameters.txt").exists());
        assertTrue(new File(dir, "summary.txt").exists());
        assertTrue(new File(dir, "experiment_summary.txt").exists());
        assertTrue(new File(dir, "timeseries.csv").exists());
        assertTrue(new File(dir, "transport.csv").exists());
        assertTrue(new File(dir, "energy.csv").exists());
        assertTrue(new File(dir, "topology.csv").exists());
        assertTrue(new File(dir, "fielddepth.csv").exists());
        assertTrue(new File(dir, "clamp.csv").exists());
        assertTrue(new File(dir, "activation_grid.csv").exists());
        assertTrue(new File(dir, "memory_grid.csv").exists());
        assertTrue(new File(dir, "fielddepth_grid.csv").exists());
    }

    @Test
    void writesPostInjectionDecayExperimentArtifacts() throws Exception {
        File dir = Files.createTempDirectory("post-injection-decay").toFile();
        GameplayValidationExperiments experiments = new GameplayValidationExperiments();
        ParameterSet parameters = new ParameterSet();
        parameters.v3FieldDepthGain = 0.01;
        parameters.v3FieldDepthDecay = 0.0001;
        parameters.v3FieldDepthRadius = 3.0;
        parameters.v3FieldDepthEffectK = 0.1;
        parameters.v3DistanceDecay = 1.0;
        Method method = GameplayValidationExperiments.class.getDeclaredMethod(
                "runPostInjectionDecay", ParameterSet.class, int.class, int.class, int.class, File.class);
        method.setAccessible(true);
        method.invoke(experiments, parameters, 21, 21, 500, dir);

        File expDir = new File(dir, "post_injection_decay");
        assertTrue(new File(expDir, "run.log").exists());
        assertTrue(new File(expDir, "summary.txt").exists());
        assertTrue(new File(expDir, "experiment_summary.txt").exists());
        assertTrue(new File(expDir, "decay_analysis.txt").exists());
        assertTrue(new File(expDir, "timeseries.csv").exists());
        assertTrue(new File(expDir, "topology.csv").exists());
        assertTrue(new File(expDir, "transport.csv").exists());
        assertTrue(new File(expDir, "activation_tick_0000.csv").exists());
        assertTrue(new File(expDir, "activation_tick_0100.csv").exists());
        assertTrue(new File(expDir, "activation_tick_0200.csv").exists());
        assertTrue(new File(expDir, "activation_tick_0500.csv").exists());
        assertTrue(new File(expDir, "activation_tick_1000.csv").exists());
        assertTrue(new File(expDir, "activation_tick_2000.csv").exists());
        assertTrue(new File(expDir, "activation_tick_3000.csv").exists());
        assertTrue(new File(expDir, "memory_tick_0000.csv").exists());
        assertTrue(new File(expDir, "memory_tick_0100.csv").exists());
        assertTrue(new File(expDir, "memory_tick_0200.csv").exists());
        assertTrue(new File(expDir, "memory_tick_0500.csv").exists());
        assertTrue(new File(expDir, "memory_tick_1000.csv").exists());
        assertTrue(new File(expDir, "memory_tick_2000.csv").exists());
        assertTrue(new File(expDir, "memory_tick_3000.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_0000.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_0100.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_0200.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_0500.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_1000.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_2000.csv").exists());
        assertTrue(new File(expDir, "fielddepth_tick_3000.csv").exists());
    }
}
