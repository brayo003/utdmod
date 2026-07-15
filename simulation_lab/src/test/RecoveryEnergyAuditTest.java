import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecoveryEnergyAuditTest {
    @Test
    public void recoveryAuditTracksPerTickEnergyTerms() {
        ParameterSet parameters = new ParameterSet();
        GridSimulationV2 simulation = new GridSimulationV2(3, 3, 5, parameters);

        List<GridSimulationV2.EnergyAuditRow> rows = simulation.runRecoveryEnergyAudit(2, 3, 1, 1, 1.0);

        assertFalse(rows.isEmpty());
        assertEquals(5, rows.size());

        GridSimulationV2.EnergyAuditRow lastRow = rows.get(rows.size() - 1);
        assertEquals(4, lastRow.tick);
        assertEquals(lastRow.injectionContribution + lastRow.relaxationContribution + lastRow.baselineContribution + lastRow.memoryContribution + lastRow.depthContribution,
                lastRow.netDeltaActivation, 1e-9);
        assertTrue(Double.isFinite(lastRow.baselineDrive));
        assertTrue(Double.isFinite(lastRow.baselineHealing));
        assertTrue(Double.isFinite(lastRow.memoryGain));
        assertTrue(Double.isFinite(lastRow.memoryDecay));
        assertTrue(Double.isFinite(lastRow.depthGain));
        assertTrue(Double.isFinite(lastRow.depthDecay));

        for (int i = 2; i < rows.size(); i++) {
            GridSimulationV2.EnergyAuditRow row = rows.get(i);
            assertEquals(0.0, row.injectionContribution, 1e-12);
        }
    }
}
