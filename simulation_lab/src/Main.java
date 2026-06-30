import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        Path outputDir = Path.of("output");
        Files.createDirectories(outputDir);
        ExperimentRunner runner = new ExperimentRunner();
        runner.runAll(outputDir.resolve("csv").resolve("RESULTS.csv").toString(), outputDir.toString());
    }
}
