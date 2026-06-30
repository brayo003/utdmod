import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class GameplayExperimentAuditTest {
    private final String auditDir = "run/gameplay_validation/audit";

    @Test
    void auditAllGameplayExperiments() throws Exception {
        File d = new File(auditDir);
        if (!d.exists()) d.mkdirs();

        ParameterSet p = new ParameterSet();
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.v3FieldDepthRadius = 3.0;
        p.v3FieldDepthEffectK = 0.1;
        p.v3DistanceDecay = 1.0;

        auditSustainThreshold(p, 21, 21, 500, 5000, d);
        auditExpansionRadius(p, 21, 21, 500, 5000, new int[]{50, 100, 250}, d);
        auditRecruitmentSpeed(p, 41, 41, 500, 5000, 10, d);
        auditRecoveryTime(p, 21, 21, 500, 5000, d);
        auditBasinSize(p, 21, 21, 500, 5000, new double[]{0.01, 0.02, 0.05, 0.1, 0.2}, d);
        auditHistoricalRoutes(p, 41, 41, 500, 5000, d);
        auditAncientCity(p, 61, 41, new int[]{500, 5000, 10000}, d);
    }

    private void auditSustainThreshold(ParameterSet p, int W, int H, int buildShort, int buildLong, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_sustain_threshold.txt"))) {
            w.println("Experiment 1: Sustaining Activity Threshold");
            w.println("Intended measurement: minimum repeated injection frequency to keep hotspot above threshold.");

            auditSustainCase(w, p, W, H, buildShort, "young", true);
            auditSustainCase(w, p, W, H, buildLong, "old", true);
        }
    }

    private void auditSustainCase(PrintWriter w, ParameterSet p, int W, int H, int buildTicks, String label, boolean writeProbe) {
        int cx = W / 2, cy = H / 2;
        AuditState state = buildRepeatedInjectionState(p, W, H, buildTicks, cx, cy);
        w.println("\n--- " + label + " region (build=" + buildTicks + ") ---");
        writeStateSummary(w, state, p, cx, cy, buildTicks, "build-only");
        writeStateGrids(w, state);
        w.println("Intended hotspot region: center cell only.");
        verifySingleHotspot(w, state, p.regionActiveThreshold, cx, cy, 0);
        if (writeProbe) {
            w.println("Probe design: repeated injection at center cell at intervals 1, 2, 5, 10, 20, 50, 100 in the original experiment.");
            writeProbeSummary(w, state, p, cx, cy, 50, true, 1);
        }
    }

    private void auditExpansionRadius(ParameterSet p, int W, int H, int buildShort, int buildLong, int[] ticks, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_expansion_radius.txt"))) {
            w.println("Experiment 2: Expansion Radius");
            w.println("Intended measurement: how far a disturbance spreads after 50/100/250 ticks.");
            auditExpansionCase(w, p, W, H, buildShort, "young", ticks);
            auditExpansionCase(w, p, W, H, buildLong, "old", ticks);
        }
    }

    private void auditExpansionCase(PrintWriter w, ParameterSet p, int W, int H, int buildTicks, String label, int[] ticks) {
        int cx = W / 2, cy = H / 2;
        int disturbX = cx + 2, disturbY = cy;
        AuditState state = buildRepeatedInjectionState(p, W, H, buildTicks, cx, cy);
        w.println("\n--- " + label + " region (build=" + buildTicks + ") ---");
        writeStateSummary(w, state, p, cx, cy, buildTicks, "build-only");
        writeStateGrids(w, state);
        w.println("Pre-probe: disturbance will be injected at (" + disturbX + "," + disturbY + ") before tick 0.");
        verifySingleHotspot(w, state, p.regionActiveThreshold, cx, cy, 2);
        writeProbeSummaryForExpansion(w, state, p, cx, cy, disturbX, disturbY, 50, ticks);
    }

    private void auditRecruitmentSpeed(ParameterSet p, int W, int H, int buildShort, int buildLong, int separation, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_recruitment_speed.txt"))) {
            w.println("Experiment 3: Recruitment Speed");
            w.println("Intended measurement: how quickly a stimulated settlement recruits a separated settlement.");
            auditRecruitmentCase(w, p, W, H, buildShort, "young", separation);
            auditRecruitmentCase(w, p, W, H, buildLong, "old", separation);
        }
    }

    private void auditRecruitmentCase(PrintWriter w, ParameterSet p, int W, int H, int buildTicks, String label, int separation) {
        int ax = W / 2 - separation / 2;
        int ay = H / 2;
        int bx = ax + separation;
        int by = ay;
        AuditState state = buildRepeatedInjectionState(p, W, H, buildTicks, ax, ay);
        w.println("\n--- " + label + " region (build=" + buildTicks + ") ---");
        writeStateSummary(w, state, p, ax, ay, buildTicks, "build-only");
        writeStateGrids(w, state);
        w.println("Intended source hotspot: A at (" + ax + "," + ay + "); target B at (" + bx + "," + by + ") should be inactive before probe.");
        verifySingleHotspot(w, state, p.regionActiveThreshold, ax, ay, 0);
        verifyCellBelowThreshold(w, state, p.regionActiveThreshold, bx, by);
        writeProbeSummaryForRecruitment(w, state, p, ax, ay, bx, by, 50);
    }

    private void auditRecoveryTime(ParameterSet p, int W, int H, int buildShort, int buildLong, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_recovery_time.txt"))) {
            w.println("Experiment 4: Recovery Time");
            w.println("Intended measurement: how long activation and memory take to fall below threshold after activity stops.");
            auditRecoveryCase(w, p, W, H, buildShort, "young");
            auditRecoveryCase(w, p, W, H, buildLong, "old");
        }
    }

    private void auditRecoveryCase(PrintWriter w, ParameterSet p, int W, int H, int buildTicks, String label) {
        int cx = W / 2, cy = H / 2;
        AuditState state = buildRepeatedInjectionState(p, W, H, buildTicks, cx, cy);
        w.println("\n--- " + label + " region (build=" + buildTicks + ") ---");
        writeStateSummary(w, state, p, cx, cy, buildTicks, "build-only");
        writeStateGrids(w, state);
        w.println("Probe design: no further injection, only decay." );
        writeProbeSummary(w, state, p, cx, cy, 50, false, -1);
    }

    private void auditBasinSize(ParameterSet p, int W, int H, int buildShort, int buildLong, double[] mags, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_basin_size.txt"))) {
            w.println("Experiment 5: Basin Size");
            w.println("Intended measurement: whether a tiny disturbance can reactivate a decayed hotspot.");
            auditBasinCase(w, p, W, H, buildShort, "young", mags);
            auditBasinCase(w, p, W, H, buildLong, "old", mags);
        }
    }

    private void auditBasinCase(PrintWriter w, ParameterSet p, int W, int H, int buildTicks, String label, double[] mags) {
        int cx = W/2, cy = H/2;
        AuditState state = buildRepeatedInjectionState(p, W, H, buildTicks, cx, cy);
        // decay 500 ticks before perturbation
        for (int t=0; t<500; t++) stepOnce(state.activation, state.memory, state.depth, p, t, cx, cy, false);
        w.println("\n--- " + label + " region (build=" + buildTicks + ") ---");
        writeStateSummary(w, state, p, cx, cy, buildTicks, "post-decay");
        writeStateGrids(w, state);
        verifyActivationNearZero(w, state, p.regionActiveThreshold);
        for (double mag : mags) {
            AuditState copy = state.copy();
            copy.activation[cy][cx] += mag;
            w.println("\nPerturbation magnitude=" + mag + " at (" + cx + "," + cy + ")");
            writeProbeSummary(w, copy, p, cx, cy, 50, false, -1);
        }
    }

    private void auditHistoricalRoutes(ParameterSet p, int W, int H, int buildShort, int buildLong, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_historical_routes.txt"))) {
            w.println("Experiment 6: Historical Trade Corridor");
            w.println("Intended measurement: whether a historical route transmits activation farther than an unused route.");
            auditHistoricalCase(w, p, W, H, buildShort, "young");
            auditHistoricalCase(w, p, W, H, buildLong, "old");
        }
    }

    private void auditHistoricalCase(PrintWriter w, ParameterSet p, int W, int H, int buildTicks, String label) {
        int sx=2, sy=H/2;
        int usedX = Math.min(W-1, sx+15);
        int usedY = sy;
        int unusedY=sy+3, unusedX=usedX;
        AuditState state = buildHistoricalRouteState(p, W, H, buildTicks, sx, sy);
        w.println("\n--- " + label + " region (build=" + buildTicks + ") ---");
        writeStateSummary(w, state, p, sx, sy, buildTicks, "route-build");
        writeStateGrids(w, state);
        w.println("Intended used route: horizontally along y=" + sy + ". Unused route: vertical offset " + (unusedY-sy) + ".");
        verifyRouteInactivity(w, state, p.regionActiveThreshold, unusedX, unusedY);
        writeProbeSummaryForHistorical(w, state, p, sx, sy, usedX, usedY, unusedX, unusedY, 50);
    }

    private void auditAncientCity(ParameterSet p, int W, int H, int[] builds, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new File(outDir, "audit_ancient_city.txt"))) {
            w.println("Experiment 7: Ancient City Simulation");
            w.println("Intended measurement: whether older settlement infrastructure develops larger sphere of influence.");
            for (int build : builds) {
                AuditState state = buildAncientCityState(p, W, H, build);
                w.println("\n--- build=" + build + " ---");
                writeStateSummary(w, state, p, 10, H/2, build, "city-build");
                writeStateGrids(w, state);
                w.println("Probe: inject at village center and track first 50 ticks.");
                writeProbeSummary(w, state, p, 10, H/2, 50, false, -1);
            }
        }
    }

    private AuditState buildRepeatedInjectionState(ParameterSet p, int W, int H, int buildTicks, int centerX, int centerY) {
        double[][] A = new double[H][W];
        double[][] M = new double[H][W];
        double[][] C = new double[H][W];
        for (int t=0; t<buildTicks; t++) {
            A[centerY][centerX] += 1.0;
            stepOnce(A, M, C, p, t, centerX, centerY, false);
        }
        return new AuditState(A,M,C);
    }

    private AuditState buildHistoricalRouteState(ParameterSet p, int W, int H, int buildTicks, int sx, int sy) {
        double[][] A = new double[H][W];
        double[][] M = new double[H][W];
        double[][] C = new double[H][W];
        for (int t=0; t<buildTicks; t++) {
            int pathX = sx + t % 10;
            A[sy][pathX] += 1.0;
            stepOnce(A, M, C, p, t, sx, sy, false);
        }
        for (int t=0; t<50; t++) stepOnce(A,M,C,p,t,sx,sy,false);
        return new AuditState(A,M,C);
    }

    private AuditState buildAncientCityState(ParameterSet p, int W, int H, int buildTicks) {
        int cx = 10, cy = H/2;
        double[][] A = new double[H][W];
        double[][] M = new double[H][W];
        double[][] C = new double[H][W];
        for (int t=0; t<buildTicks; t++) {
            for (int dy=-1; dy<=1; dy++) for (int dx=-1; dx<=1; dx++) A[cy+dy][cx+dx]+=1.0;
            A[cy][cx+6]+=1.0;
            for (int i=0; i<5; i++) A[cy][cx+i+2]+=0.5;
            A[cy+2][cx-2]+=1.0; A[cy+2][cx-1]+=1.0;
            stepOnce(A,M,C,p,t,cx,cy,false);
        }
        return new AuditState(A,M,C);
    }

    private void writeStateSummary(PrintWriter w, AuditState state, ParameterSet p, int sourceX, int sourceY, int buildTicks, String phase) {
        w.println("Phase: " + phase + ", source=(" + sourceX + "," + sourceY + ")");
        w.println("Grid size: " + state.activation[0].length + "x" + state.activation.length);
        w.println("Build ticks: " + buildTicks);
        w.println("Threshold: " + p.regionActiveThreshold + ", clamp max: " + p.v2ClampMax);
        w.println("Max activation: " + maxValue(state.activation));
        w.println("Max memory: " + maxValue(state.memory));
        w.println("Max field depth: " + maxValue(state.depth));
        w.println("Active cells above threshold: " + MetricEvaluator.countActiveCells(state.activation, p.regionActiveThreshold, 0.0));
        w.println("Field depth at source: " + state.depth[sourceY][sourceX]);
        w.println("Effective source radius at source: " + effectiveRadius(p, state.depth[sourceY][sourceX]));
        w.println("Source activation: " + state.activation[sourceY][sourceX] + ", source memory: " + state.memory[sourceY][sourceX]);
    }

    private void writeStateGrids(PrintWriter w, AuditState state) {
        w.println("Activation grid:"); writeGrid(w, state.activation);
        w.println("Memory grid:"); writeGrid(w, state.memory);
        w.println("Field depth grid:"); writeGrid(w, state.depth);
    }

    private void writeGrid(PrintWriter w, double[][] grid) {
        for (double[] row : grid) {
            for (int x=0; x<row.length; x++) {
                w.print(String.format("%.3f", row[x]));
                if (x < row.length-1) w.print(",");
            }
            w.println();
        }
    }

    private void verifySingleHotspot(PrintWriter w, AuditState state, double threshold, int cx, int cy, int radius) {
        int outside = 0;
        int total = 0;
        for (int y=0; y<state.activation.length; y++) {
            for (int x=0; x<state.activation[0].length; x++) {
                if (Math.abs(x-cx) <= radius && Math.abs(y-cy) <= radius) continue;
                if (state.activation[y][x] >= threshold) outside++;
                total++;
            }
        }
        w.println("Active cells above threshold outside intended hotspot radius " + radius + ": " + outside + " of " + total);
    }

    private void verifyCellBelowThreshold(PrintWriter w, AuditState state, double threshold, int tx, int ty) {
        double value = state.activation[ty][tx];
        w.println("Cell (" + tx + "," + ty + ") activation before probe: " + value + " (threshold=" + threshold + ")");
    }

    private void verifyRouteInactivity(PrintWriter w, AuditState state, double threshold, int x, int y) {
        double value = state.activation[y][x];
        w.println("Unused route cell (" + x + "," + y + ") activation before probe: " + value + " (threshold=" + threshold + ")");
    }

    private void verifyActivationNearZero(PrintWriter w, AuditState state, double threshold) {
        double max = maxValue(state.activation);
        w.println("Max activation before perturbation after decay: " + max + ", threshold=" + threshold + ".");
        if (max >= threshold) {
            w.println("WARNING: activation is still above threshold before perturbation. This undermines the basin experiment.");
        }
    }

    private void writeProbeSummary(PrintWriter w, AuditState initial, ParameterSet p, int sourceX, int sourceY, int ticks, boolean withInjection, int injectionInterval) {
        w.println("Probe trace (first " + ticks + " ticks):");
        w.println("tick,maxActivation,activeCells,diffusionRadius,sourceActivation,sourceMemory,clampHit");
        AuditState state = initial.copy();
        for (int t=0; t<ticks; t++) {
            if (withInjection && injectionInterval > 0 && t % injectionInterval == 0) {
                state.activation[sourceY][sourceX] += 1.0;
            }
            stepOnce(state.activation, state.memory, state.depth, p, t, sourceX, sourceY, false);
            double maxA = maxValue(state.activation);
            int activeCount = MetricEvaluator.countActiveCells(state.activation, p.regionActiveThreshold, 0.0);
            double radius = MetricEvaluator.diffusionRadius(state.activation, p.regionActiveThreshold, maxA, sourceX, sourceY);
            boolean clampHit = maxA >= p.v2ClampMax - 1e-9;
            w.println(String.format("%d,%.6f,%d,%.6f,%.6f,%.6f,%b", t, maxA, activeCount, radius, state.activation[sourceY][sourceX], state.memory[sourceY][sourceX], clampHit));
        }
        w.println("Explanation:");
        w.println("- maxActivation is the peak activation on the grid this tick.");
        w.println("- activeCells counts cells at or above the hotspot threshold.");
        w.println("- diffusionRadius is distance from source to furthest active cell.");
        w.println("- clampHit shows whether activation has reached the activation cap.");
    }

    private void writeProbeSummaryForExpansion(PrintWriter w, AuditState initial, ParameterSet p, int cx, int cy, int disturbX, int disturbY, int ticks, int[] samplePoints) {
        w.println("Probe trace (first " + ticks + " ticks) for expansion radius:");
        w.println("tick,maxActivation,activeCells,diffusionRadius,disturbSourceActivation,disturbSourceDepth,clampHit");
        AuditState state = initial.copy();
        state.activation[disturbY][disturbX] += 1.0;
        for (int t=0; t<ticks; t++) {
            stepOnce(state.activation, state.memory, state.depth, p, t, cx, cy, false);
            double maxA = maxValue(state.activation);
            int activeCount = MetricEvaluator.countActiveCells(state.activation, p.regionActiveThreshold, 0.0);
            double radius = MetricEvaluator.diffusionRadius(state.activation, p.regionActiveThreshold, maxA, disturbX, disturbY);
            boolean clampHit = maxA >= p.v2ClampMax - 1e-9;
            w.println(String.format("%d,%.6f,%d,%.6f,%.6f,%.6f,%b", t, maxA, activeCount, radius, state.activation[disturbY][disturbX], state.depth[disturbY][disturbX], clampHit));
            if (contains(samplePoints, t)) {
                w.println("# Sampled expansion radius at " + t + " = " + radius);
            }
        }
        w.println("Explanation:");
        w.println("- The radius is measured from the injected disturbance location.");
        w.println("- If the disturbance is already active off-target before probe, the experiment is invalid.");
    }

    private void writeProbeSummaryForRecruitment(PrintWriter w, AuditState initial, ParameterSet p, int ax, int ay, int bx, int by, int ticks) {
        w.println("Probe trace (first " + ticks + " ticks) for recruitment:");
        w.println("tick,maxActivation,activeCells,Aactivation,Bactivation,Adistance,Bdistance,clampHit");
        AuditState state = initial.copy();
        for (int t=0; t<ticks; t++) {
            state.activation[ay][ax] += 1.0;
            stepOnce(state.activation, state.memory, state.depth, p, t, ax, ay, false);
            double maxA = maxValue(state.activation);
            int activeCount = MetricEvaluator.countActiveCells(state.activation, p.regionActiveThreshold, 0.0);
            boolean clampHit = maxA >= p.v2ClampMax - 1e-9;
            w.println(String.format("%d,%.6f,%d,%.6f,%.6f,%.6f,%.6f,%b", t, maxA, activeCount, state.activation[ay][ax], state.activation[by][bx], distance(ax,ay,ax,ay), distance(ax,ay,bx,by), clampHit));
        }
        w.println("Explanation:");
        w.println("- Recruitment at tick 0 means B crosses threshold immediately after the first probe step.");
        w.println("- This may occur if B is already near threshold before probe or if transport can carry activity across separation in one step.");
    }

    private void writeProbeSummaryForHistorical(PrintWriter w, AuditState initial, ParameterSet p, int sx, int sy, int usedX, int usedY, int unusedX, int unusedY, int ticks) {
        w.println("Probe trace (first " + ticks + " ticks) for historical routes:");
        w.println("tick,maxActivation,activeCells,routeActivation,unusedActivation,clampHit");
        AuditState state = initial.copy();
        state.activation[sy][sx] += 1.0;
        for (int t=0; t<ticks; t++) {
            stepOnce(state.activation, state.memory, state.depth, p, t, sx, sy, false);
            double maxA = maxValue(state.activation);
            int activeCount = MetricEvaluator.countActiveCells(state.activation, p.regionActiveThreshold, 0.0);
            boolean clampHit = maxA >= p.v2ClampMax - 1e-9;
            w.println(String.format("%d,%.6f,%d,%.6f,%.6f,%b", t, maxA, activeCount, state.activation[usedY][usedX], state.activation[unusedY][unusedX], clampHit));
        }
        w.println("Explanation:");
        w.println("- The historical route is only meaningful if the used route has higher activation than the unused route after the same probe.");
    }

    private boolean contains(int[] list, int value) {
        for (int x : list) if (x == value) return true;
        return false;
    }

    private double maxValue(double[][] grid) {
        double max = Double.NEGATIVE_INFINITY;
        for (double[] row : grid) for (double value : row) max = Math.max(max, value);
        return max;
    }

    private int effectiveRadius(ParameterSet p, double depthValue) {
        return 1 + (int) Math.floor(p.v3FieldDepthRadius * depthValue / (depthValue + p.v3FieldDepthEffectK));
    }

    private double distance(int x1, int y1, int x2, int y2) {
        return Math.hypot(x2-x1, y2-y1);
    }

    private static class AuditState {
        final double[][] activation;
        final double[][] memory;
        final double[][] depth;

        AuditState(double[][] activation, double[][] memory, double[][] depth) {
            this.activation = activation;
            this.memory = memory;
            this.depth = depth;
        }

        AuditState copy() {
            int h = activation.length;
            int w = activation[0].length;
            double[][] a = new double[h][w];
            double[][] m = new double[h][w];
            double[][] d = new double[h][w];
            for (int y=0; y<h; y++) {
                System.arraycopy(activation[y], 0, a[y], 0, w);
                System.arraycopy(memory[y], 0, m[y], 0, w);
                System.arraycopy(depth[y], 0, d[y], 0, w);
            }
            return new AuditState(a,m,d);
        }
    }

    private static void stepOnce(double[][] activation, double[][] memory, double[][] depth, ParameterSet parameters, int tick, int injectX, int injectY, boolean inject) {
        int height = activation.length;
        int width = activation[0].length;
        double[][] nextA = new double[height][width];
        double[][] nextM = new double[height][width];
        double[][] nextC = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double A = activation[y][x];
                double M = memory[y][x];
                double C = depth[y][x];
                double transport = 0.0;
                int maxSearchRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius);
                for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                    for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            double distance = Math.sqrt(dx * dx + dy * dy);
                            if (distance > maxSearchRadius) continue;
                            double nA = activation[ny][nx];
                            double nM = memory[ny][nx];
                            double nC = depth[ny][nx];
                            int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                            if (distance > sourceRadius) continue;
                            double neighborStim = nA / (1.0 + nA);
                            neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                            double weight = 1.0;
                            if (parameters.v3DistanceDecay > 0.0) {
                                weight = Math.exp(-distance / parameters.v3DistanceDecay);
                            } else {
                                weight = 1.0 / (1.0 + distance);
                            }
                            transport += neighborStim * weight;
                        }
                    }
                }
                transport *= parameters.v2TransportGain;
                transport *= (1.0 - A / (1.0 + A));

                double external = 0.0;
                if (inject && x == injectX && y == injectY) external = 1.0;

                double memTerm = (1.0 - (M / (M + parameters.v2MemoryEffectK)));
                double decayFactor = parameters.v2ActivationDecay * memTerm;
                double nextActivation = Math.max(0.0, A * Math.max(0.0, 1.0 - decayFactor) + transport + external);
                if (parameters.v2ClampMax > 0.0) nextActivation = Math.min(nextActivation, parameters.v2ClampMax);

                double deltaMemory = parameters.v2MemoryGain * (A / (1.0 + A));
                double nextMemory = Math.max(0.0, M + deltaMemory - parameters.v2MemoryDecay * M);

                double deltaDepth = parameters.v3FieldDepthGain * (A * M);
                if (deltaDepth > 1.0) deltaDepth = 1.0;
                double nextDepth = Math.max(0.0, C + deltaDepth - parameters.v3FieldDepthDecay * C);

                nextA[y][x] = nextActivation;
                nextM[y][x] = nextMemory;
                nextC[y][x] = nextDepth;
            }
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(nextA[y], 0, activation[y], 0, width);
            System.arraycopy(nextM[y], 0, memory[y], 0, width);
            System.arraycopy(nextC[y], 0, depth[y], 0, width);
        }
    }
}
