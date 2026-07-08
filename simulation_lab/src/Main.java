import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        Path outputDir = Path.of("output");
        Files.createDirectories(outputDir);
        HybridRelaxationReport.writeReport(outputDir.resolve("hybrid_relaxation"));
        ParameterSpaceCharacterizationReport.writeReport(outputDir.resolve("parameter_space_characterization"));
        WorldEvolutionExperiment.run(outputDir.resolve("world_evolution"));
        RegionPhaseExperiment.run(outputDir.resolve("region_phase"));
    }
}
