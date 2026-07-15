import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EcologicalValidationSuiteTest {
    @Test
    public void ecologicalScenariosShouldGenerateReportsAndDiagnostics() throws Exception {
        Path outputDir = Path.of("build/test-output/ecological-validation");
        Files.createDirectories(outputDir);

        List<EcologicalScenarioResult> results = EcologicalValidationSuite.runAllScenarios(outputDir.toString(), 8, 16, 16, 1200);

        assertEquals(8, results.size(), "expected one canonical scenario result per ecological process");
        assertTrue(Files.exists(outputDir.resolve("scenario_summary.csv")), "summary CSV should be written");
        assertTrue(Files.exists(outputDir.resolve("scenario_reports.txt")), "scenario report should be written");
        assertTrue(Files.exists(outputDir.resolve("scenario_1_persistent_travel_corridor.csv")), "scenario CSV should be written");
        assertTrue(Files.exists(outputDir.resolve("spatial_validation_report.txt")), "spatial validation report should be written");
        assertTrue(Files.exists(outputDir.resolve("scenario_1_persistent_travel_corridor/spatial_metrics.csv")), "spatial metric CSV should be written");
        assertTrue(Files.exists(outputDir.resolve("scenario_1_persistent_travel_corridor/spatial_snapshot_0000.csv")), "spatial snapshot should be written");
    }
}
