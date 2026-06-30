import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExperimentFrameworkTest {
    @Test
    void baselineReportIsGenerated() throws Exception {
        Path outputDir = Path.of("build/test-output");
        Files.createDirectories(outputDir);
        ExperimentRunner runner = new ExperimentRunner();
        runner.runAll(outputDir.resolve("RESULTS.csv").toString(), outputDir.toString());
        assertTrue(Files.exists(outputDir.resolve("RESULTS.csv")));
        assertTrue(Files.exists(outputDir.resolve("SUMMARY.md")));
    }
}
