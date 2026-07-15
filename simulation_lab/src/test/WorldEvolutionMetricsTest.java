import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorldEvolutionMetricsTest {
    @Test
    public void countsConnectedRegionsAndLargestRegion() {
        double[][] grid = {
                {0.0, 0.0, 1.0, 0.0},
                {0.0, 1.0, 1.0, 0.0},
                {0.0, 0.0, 0.0, 1.0},
                {1.0, 0.0, 0.0, 0.0}
        };
        WorldEvolutionExperiment.ConnectedRegionMetrics metrics = WorldEvolutionExperiment.computeConnectedRegionMetrics(grid, 0.5);
        assertEquals(3, metrics.regionCount());
        assertEquals(3, metrics.largestRegionSize());
    }
}
