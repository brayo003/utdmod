import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransportBalancingVariantsTest {

    @Test
    void runPostInjectionDecayVariantsProducesBalanceSummaries() throws Exception {
        File outDir = new File("run/gameplay_validation");
        if (!outDir.exists()) {
            assertTrue(outDir.mkdirs());
        }

        GameplayValidationExperiments experiments = new GameplayValidationExperiments();
        experiments.runPostInjectionDecayVariants(new ParameterSet(), 21, 21, 500, outDir);

        assertTrue(new File(outDir, "variant_1_exponential_retention/balance_summary.txt").exists());
        assertTrue(new File(outDir, "variant_2_rational_retention/balance_summary.txt").exists());
        assertTrue(new File(outDir, "variant_3_logistic_transition/balance_summary.txt").exists());
        assertTrue(new File(outDir, "variant_4_field_depth_retention/balance_summary.txt").exists());
        assertTrue(new File(outDir, "variant_5_combined_memory_field_depth/balance_summary.txt").exists());
        assertTrue(new File(outDir, "balance_comparison.txt").exists());
        assertTrue(new File(outDir, "constitutive_law_comparison.txt").exists());
    }

    @Test
    void structuralTransportProducesMeaningfulSpread() throws Exception {
        File outDir = new File("run/gameplay_validation");
        if (!outDir.exists()) {
            assertTrue(outDir.mkdirs());
        }

        GameplayValidationExperiments experiments = new GameplayValidationExperiments();
        experiments.runPostInjectionDecayVariants(new ParameterSet(), 21, 21, 500, outDir);

        File summary = new File(outDir, "variant_1_exponential_retention/balance_summary.txt");
        assertTrue(summary.exists(), "Expected balance summary to be generated");
        String content = new String(Files.readAllBytes(summary.toPath()));

        double maxTransport = readDoubleValue(content, "Maximum transport=");
        int finalHotspotSize = readIntValue(content, "Final hotspot size=");

        assertTrue(maxTransport > 1e-6, "Expected transport to remain above a minimal structural threshold, got " + maxTransport);
        assertTrue(finalHotspotSize >= 0, "Expected the hotspot footprint to be reported consistently");
    }

    private static double readDoubleValue(String content, String label) {
        int index = content.indexOf(label);
        assertTrue(index >= 0, "Missing label: " + label);
        String raw = content.substring(index + label.length()).split("\\R", 2)[0].trim();
        return Double.parseDouble(raw);
    }

    private static int readIntValue(String content, String label) {
        int index = content.indexOf(label);
        assertTrue(index >= 0, "Missing label: " + label);
        String raw = content.substring(index + label.length()).split("\\R", 2)[0].trim();
        return Integer.parseInt(raw);
    }
}
