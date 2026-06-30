import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricValidationTest {
    @Test
    void singleCellProducesExpectedSimpleMetrics() {
        double[][] field = {{1.0}};
        assertEquals(1, MetricEvaluator.countActiveCells(field, 0.05, 1.0));
        assertEquals(1, MetricEvaluator.countHotspots(field, 0.05, 1.0));
        assertEquals(1, MetricEvaluator.largestRegion(field, 0.05, 1.0));
        assertEquals(0.0, MetricEvaluator.diffusionRadius(field, 0.05, 1.0, 0, 0), 1e-9);
    }

    @Test
    void separateIslandsReportLargestRegionOnly() {
        double[][] field = {
                {1.0, 1.0, 0.0, 0.0},
                {1.0, 1.0, 0.0, 0.0},
                {0.0, 0.0, 1.0, 1.0},
                {0.0, 0.0, 1.0, 1.0}
        };
        assertEquals(4, MetricEvaluator.largestRegion(field, 0.05, 1.0));
        assertEquals(8, MetricEvaluator.countActiveCells(field, 0.05, 1.0));
    }

    @Test
    void linearGradientHasExpectedCenterOfMassAndGradient() {
        double[][] field = {
                {0.0, 1.0, 2.0},
                {0.0, 1.0, 2.0},
                {0.0, 1.0, 2.0}
        };
        assertEquals(Math.sqrt(5.0 / 18.0), MetricEvaluator.centerOfMass(field), 1e-9);
        assertEquals(1.0, MetricEvaluator.maxGradient(field), 1e-9);
    }

    @Test
    void uniformFieldHasNoHotspotsAndFullLargestRegion() {
        double[][] field = {
                {1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0}
        };
        assertEquals(0, MetricEvaluator.countHotspots(field, 0.05, 1.0));
        assertEquals(9, MetricEvaluator.largestRegion(field, 0.05, 1.0));
        assertEquals(0.0, MetricEvaluator.maxGradient(field), 1e-9);
    }

    @Test
    void oscillationAmplitudeUsesTimeSeriesRange() {
        List<Double> history = List.of(0.0, 1.0, 0.0, 1.0);
        assertEquals(1.0, MetricEvaluator.oscillationAmplitude(history), 1e-9);
    }

    @Test
    void halfLifeIsObservedFromDecayHistory() {
        List<Double> history = List.of(1.0, 0.8, 0.5, 0.2);
        assertEquals(2.0, MetricEvaluator.halfLife(history), 1e-9);
    }
}
