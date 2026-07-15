import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OperatingRegionCharacterizerTest {
    @Test
    public void classifiesDeadQuietLivingPersistentAndSaturatedRegions() {
        assertEquals("Dead", OperatingRegionCharacterizer.classifyEquilibrium(0.001, 0.001, 0.001, 0.001, 1.0));
        assertEquals("Quiet", OperatingRegionCharacterizer.classifyEquilibrium(0.05, 0.1, 0.05, 0.05, 50.0));
        assertEquals("Living", OperatingRegionCharacterizer.classifyEquilibrium(0.5, 1.0, 0.5, 0.5, 200.0));
        assertEquals("Persistent", OperatingRegionCharacterizer.classifyEquilibrium(2.0, 3.0, 6.0, 2.0, 5000.0));
        assertEquals("Saturated", OperatingRegionCharacterizer.classifyEquilibrium(8.0, 10.0, 15.0, 8.0, 15000.0));
    }
}
