package com.utdmod.test.scenarios;

import com.utdmod.test.ScenarioContext;
import com.utdmod.test.actions.MineBlockAction;
import com.utdmod.test.actions.PersistenceRoundTripAction;
import com.utdmod.test.actions.PhaseMarkerAction;
import com.utdmod.test.actions.ScenarioAction;
import com.utdmod.test.actions.WaitTicksAction;
import com.utdmod.test.assertions.Assertion;
import com.utdmod.test.assertions.AssertionResult;
import com.utdmod.test.assertions.LambdaAssertion;
import com.utdmod.test.helpers.MetricReader;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public final class SaveLoadIntegrityScenario extends AbstractScenario {

    @Override
    public long defaultSeed() {
        return DEFAULT_SEED ^ 0x5341_5645L;
    }

    @Override
    public String id() {
        return "save_load_integrity";
    }

    @Override
    public String description() {
        return "Chunk scar/local NBT round-trip; documents global runtime persistence.";
    }

    @Override
    public List<ScenarioAction> buildActions(ScenarioContext ctx) {
        ChunkPos center = ctx.primaryChunk();
        List<ScenarioAction> steps = new ArrayList<>(standardPreamble(ctx, 2));
        steps.add(new PhaseMarkerAction("MINE_THREE"));
        for (int i = 0; i < 3; i++) {
            ChunkPos cp = new ChunkPos(center.x + i, center.z);
            steps.add(new MineBlockAction(cp, 24, 2));
            steps.add(new WaitTicksAction(60, "settle_" + i));
        }
        steps.add(new WaitTicksAction(200, "pre_save"));
        steps.add(new PersistenceRoundTripAction());
        return steps;
    }

    @Override
    public List<Assertion> buildAssertions(ScenarioContext ctx) {
        return List.of(
            new LambdaAssertion("scar_persisted_nbt", c -> {
                double delta = MetricReader.getDouble(c, "scar_delta_max");
                boolean ok = delta <= 1e-6;
                return ok
                    ? AssertionResult.pass("scar_delta_max <= 1e-6", "<= 1e-6", String.format("%.9f", delta), 1e-6, c.scenarioTick(), c.realServerTick())
                    : AssertionResult.fail("scar_delta_max <= 1e-6", "<= 1e-6", String.format("%.9f", delta), 1e-6, c.scenarioTick(), c.realServerTick());
            }),
            new LambdaAssertion("local_persisted_nbt", c -> {
                double delta = MetricReader.getDouble(c, "local_delta_max");
                boolean ok = delta <= 1e-6;
                return ok
                    ? AssertionResult.pass("local_delta_max <= 1e-6", "<= 1e-6", String.format("%.9f", delta), 1e-6, c.scenarioTick(), c.realServerTick())
                    : AssertionResult.fail("local_delta_max <= 1e-6", "<= 1e-6", String.format("%.9f", delta), 1e-6, c.scenarioTick(), c.realServerTick());
            }),
            new LambdaAssertion("global_reconstructed_consistent", c -> {
                double saved = MetricReader.getDouble(c, "saved_global");
                double loaded = MetricReader.getDouble(c, "loaded_global");
                boolean ok = Math.abs(saved - loaded) <= 1e-6;
                return ok
                    ? AssertionResult.pass(
                        "global reconstructed consistently",
                        "global reconstructed consistently",
                        String.format("saved=%.5f loaded=%.5f", saved, loaded),
                        1e-6,
                        c.scenarioTick(),
                        c.realServerTick()
                    )
                    : AssertionResult.fail(
                        "global reconstructed consistently",
                        "global reconstructed consistently",
                        String.format("saved=%.5f loaded=%.5f", saved, loaded),
                        1e-6,
                        c.scenarioTick(),
                        c.realServerTick()
                    );
            }),
            new LambdaAssertion("preconditions_met", c -> {
                double scarDelta = MetricReader.getDouble(c, "scar_delta_max");
                double savedG = MetricReader.getDouble(c, "saved_global");
                double maxScar = MetricReader.getDouble(c, "max_scar_saved");
                boolean ok = savedG > 0.03 && maxScar > 0.1;
                return ok
                    ? AssertionResult.pass(
                        "global>0.03 and scar>0.1",
                        "ok",
                        String.format("g=%.4f maxScar=%.4f scarΔ=%.6f", savedG, maxScar, scarDelta),
                        0,
                        c.scenarioTick(),
                        c.realServerTick()
                    )
                    : AssertionResult.fail(
                        "global>0.03 and scar>0.1",
                        "ok",
                        String.format("g=%.4f maxScar=%.4f", savedG, maxScar),
                        0,
                        c.scenarioTick(),
                        c.realServerTick()
                    );
            })
        );
    }
}
