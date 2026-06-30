import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GameplayOperatingRegionSweepTest {

    private final String outDir = "run/gameplay_phase_map";
    private final double[] transportGains = {0.030, 0.035, 0.040, 0.045, 0.050, 0.055};
    private final double[] distanceDecays = {1.10, 1.16, 1.22, 1.28, 1.34, 1.40};
    private final double activationDecay = 0.45;
    private final double readinessMemoryScale = 2.0;

    @Test
    void sweepGameplayOperatingRegion() throws Exception {
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();

        List<GameplayCandidate> allCandidates = new ArrayList<>();
        List<GameplayCandidate> refinementSamples = new ArrayList<>();
        List<GameplayCandidate> initialSeedPoints = new ArrayList<>();
        List<GameplayCandidate> rejectedNeighborhoods = new ArrayList<>();
        Set<String> evaluatedKeys = new HashSet<>();

        System.out.println("=== ADAPTIVE GAMEPLAY OPERATING REGION SWEEP ===");
        System.out.println("Frozen transport architecture preserved.");
        System.out.println("Using previous sweep outputs as the initial dataset and refining only around promising neighborhoods.");
        System.out.println("Outputs: " + d.getAbsolutePath());

        List<GameplayCandidate> initialDataset = loadExistingCandidateDataset(d);
        if (!initialDataset.isEmpty()) {
            List<GameplayCandidate> refreshedSeeds = new ArrayList<>();
            for (GameplayCandidate seed : initialDataset) {
                if (seed.transportGain <= 0.0 || seed.distanceDecay <= 0.0) {
                    continue;
                }
                refreshedSeeds.add(evaluateGameplayCandidate(seed.transportGain, seed.distanceDecay, activationDecay, readinessMemoryScale));
            }
            if (!refreshedSeeds.isEmpty()) {
                initialDataset = refreshedSeeds;
            }
        }
        if (initialDataset.isEmpty() || initialDataset.stream().noneMatch(c -> "PROMISING".equals(c.classification) || "NEAR_BOUNDARY".equals(c.classification))) {
            initialDataset = evaluateInitialTransitionGrid();
        }

        int completed = 0;
        int totalInitialPoints = initialDataset.size();
        for (GameplayCandidate seed : initialDataset) {
            completed++;
            allCandidates.add(seed);
            evaluatedKeys.add(formatCandidateKey(seed.transportGain, seed.distanceDecay));
            if (isTransitionCandidate(seed)) {
                initialSeedPoints.add(seed);
            }
            if (seed.classification.equals("DEAD") || seed.classification.equals("RUNAWAY")) {
                rejectedNeighborhoods.add(seed);
            }
            if (completed % 25 == 0 || completed == totalInitialPoints) {
                printProgressSummary(completed, totalInitialPoints, allCandidates, seed, false);
            }
        }

        List<GameplayCandidate> refinementSeeds = new ArrayList<>(initialSeedPoints);
        refinementSeeds.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());

        List<GameplayCandidate> regionSeeds = new ArrayList<>();
        if (refinementSeeds.isEmpty()) {
            List<GameplayCandidate> fallback = new ArrayList<>(allCandidates);
            fallback.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());
            refinementSeeds.addAll(fallback);
        }
        for (GameplayCandidate seed : refinementSeeds) {
            if (regionSeeds.size() >= 4) break;
            if (seed.classification.equals("PROMISING") || seed.classification.equals("NEAR_BOUNDARY") || seed.gameplayScore >= 0.20) {
                regionSeeds.add(seed);
            }
        }
        if (regionSeeds.isEmpty() && !allCandidates.isEmpty()) {
            regionSeeds.addAll(allCandidates.subList(0, Math.min(4, allCandidates.size())));
        }

        int plannedRefinementPoints = 0;
        for (GameplayCandidate seed : regionSeeds) {
            plannedRefinementPoints += countNeighborhoodPoints(seed);
        }
        int totalPlannedPoints = totalInitialPoints + plannedRefinementPoints;

        double bestScoreSeen = allCandidates.stream().mapToDouble(c -> c.gameplayScore).max().orElse(0.0);
        for (GameplayCandidate seed : regionSeeds) {
            List<GameplayCandidate> regionPoints = refineRegion(seed, allCandidates, refinementSamples, evaluatedKeys, rejectedNeighborhoods, completed, totalPlannedPoints);
            refinementSamples.addAll(regionPoints);
            completed += regionPoints.size();
            bestScoreSeen = Math.max(bestScoreSeen, allCandidates.stream().mapToDouble(c -> c.gameplayScore).max().orElse(bestScoreSeen));
            if (countQualifiedCandidates(allCandidates) >= 10) {
                break;
            }
        }

        if (countQualifiedCandidates(allCandidates) < 10) {
            for (GameplayCandidate seed : regionSeeds) {
                if (countQualifiedCandidates(allCandidates) >= 10) break;
                List<GameplayCandidate> regionPoints = refineRegion(seed, allCandidates, refinementSamples, evaluatedKeys, rejectedNeighborhoods, completed, totalPlannedPoints);
                refinementSamples.addAll(regionPoints);
                completed += regionPoints.size();
            }
        }

        runRepresentativeDiagnostics(d);

        writeGameplayCandidatesCsv(allCandidates, new File(d, "gameplay_candidates.csv"));
        writeBoundaryRefinementCsv(refinementSamples, new File(d, "refined_candidates.csv"));
        writeGameplayScoresCsv(allCandidates, new File(d, "gameplay_scores.csv"));
        writeBestOperatingPoints(allCandidates, new File(d, "best_operating_points.txt"));
        writeGameplayScoreDistribution(allCandidates, new File(d, "gameplay_score_distribution.csv"));
        writeCandidatePhaseMap(allCandidates, new File(d, "candidate_phase_map.csv"));
        writeGameplayWindowReport(allCandidates, new File(d, "gameplay_window_report.txt"));
        writeRecommendedReferenceConfiguration(allCandidates, new File(d, "recommended_reference_configuration.txt"));

        System.out.println("Adaptive sweep complete. Files written to " + d.getAbsolutePath());
    }

    private static final int DISTANCE_RADIUS = 15;

    private GameplayCandidate evaluateGameplayCandidate(double gain, double distDecay, double activationDecay, double readinessScale) {
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = gain;
        params.v3DistanceDecay = distDecay;
        params.v2ActivationDecay = activationDecay;
        params.readinessMemoryScale = readinessScale;
        params.v2MemoryGain = 0.02;
        params.v2MemoryDecay = 0.001;
        params.v3FieldDepthGain = 0.01;
        params.v3FieldDepthDecay = 0.0001;
        params.v3FieldDepthRadius = 0.0;
        params.transportBalanceMode = "frozen";
        params.validationMaxHotspotRadius = 12.0;

        double[][] distanceWeights = precomputeDistanceWeights(DISTANCE_RADIUS, params.v3DistanceDecay);

        GameplayCandidate candidate = new GameplayCandidate(gain, distDecay, activationDecay, readinessScale);
        candidate.persistence = measurePersistence(params, distanceWeights);
        candidate.growth = measureGrowth(params, distanceWeights);
        candidate.corridorStrength = measureCorridorStrength(params, distanceWeights);
        candidate.locality = measureLocality(params, distanceWeights);

        candidate.minerScore = clamp(0.45 * (candidate.persistence / 300.0) + 0.55 * (candidate.locality), 0.0, 1.0);
        candidate.builderScore = clamp(0.55 * (candidate.growth / 18.0) + 0.45 * (candidate.locality), 0.0, 1.0);
        candidate.traderScore = clamp(0.45 * (candidate.corridorStrength / 0.020) + 0.55 * (candidate.persistence / 300.0), 0.0, 1.0);
        candidate.explorerScore = clamp(0.60 * (candidate.growth / 18.0) + 0.40 * (candidate.locality), 0.0, 1.0);
        candidate.minimumArchetypeScore = Math.min(Math.min(candidate.minerScore, candidate.builderScore), Math.min(candidate.traderScore, candidate.explorerScore));
        candidate.gameplayScore = 0.60 * ((candidate.minerScore + candidate.builderScore + candidate.traderScore + candidate.explorerScore) / 4.0)
                + 0.40 * computeGameplayScore(candidate.persistence, candidate.growth, candidate.corridorStrength, candidate.locality);
        candidate.classification = classifyCandidate(candidate.persistence, candidate.growth, candidate.corridorStrength, candidate.locality, candidate.gameplayScore, candidate.minimumArchetypeScore);
        return candidate;
    }

    private List<GameplayCandidate> refineRegion(GameplayCandidate seed, List<GameplayCandidate> allCandidates, List<GameplayCandidate> refinementSamples,
                                                 Set<String> evaluatedKeys, List<GameplayCandidate> rejectedNeighborhoods,
                                                 int completed, int totalPlanned) {
        List<GameplayCandidate> regionPoints = new ArrayList<>();
        double gainMin = Math.max(0.02, seed.transportGain - 0.003);
        double gainMax = Math.min(0.08, seed.transportGain + 0.003);
        double decayMin = Math.max(1.00, seed.distanceDecay - 0.06);
        double decayMax = Math.min(1.60, seed.distanceDecay + 0.06);

        for (double gain = gainMin; gain <= gainMax + 1e-9; gain += 0.001) {
            for (double distDecay = decayMin; distDecay <= decayMax + 1e-9; distDecay += 0.02) {
                String key = formatCandidateKey(gain, distDecay);
                if (evaluatedKeys.contains(key)) {
                    continue;
                }
                boolean tooCloseToRejected = false;
                for (GameplayCandidate rejected : rejectedNeighborhoods) {
                    if (Math.abs(rejected.transportGain - gain) <= 0.002 && Math.abs(rejected.distanceDecay - distDecay) <= 0.03) {
                        tooCloseToRejected = true;
                        break;
                    }
                }
                if (tooCloseToRejected) {
                    continue;
                }

                GameplayCandidate candidate = evaluateGameplayCandidate(gain, distDecay, activationDecay, readinessMemoryScale);
                allCandidates.add(candidate);
                refinementSamples.add(candidate);
                evaluatedKeys.add(key);
                regionPoints.add(candidate);
                completed++;
                if (candidate.classification.equals("DEAD") || candidate.classification.equals("RUNAWAY")) {
                    rejectedNeighborhoods.add(candidate);
                }
                if (completed % 25 == 0 || completed == totalPlanned) {
                    printProgressSummary(completed, totalPlanned, allCandidates, candidate, true);
                }
            }
        }

        return regionPoints;
    }

    private boolean isTransitionCandidate(GameplayCandidate candidate) {
        return "PROMISING".equals(candidate.classification) || "NEAR_BOUNDARY".equals(candidate.classification);
    }

    private int countQualifiedCandidates(List<GameplayCandidate> allCandidates) {
        int count = 0;
        for (GameplayCandidate candidate : allCandidates) {
            if ("PROMISING".equals(candidate.classification) && candidate.gameplayScore >= 0.55 && candidate.minimumArchetypeScore >= 0.50) {
                count++;
            }
        }
        return count;
    }

    private int countNeighborhoodPoints(GameplayCandidate seed) {
        double gainSpan = Math.min(0.08, seed.transportGain + 0.003) - Math.max(0.02, seed.transportGain - 0.003);
        double decaySpan = Math.min(1.60, seed.distanceDecay + 0.06) - Math.max(1.00, seed.distanceDecay - 0.06);
        int gainSteps = (int) Math.round(gainSpan / 0.001) + 1;
        int decaySteps = (int) Math.round(decaySpan / 0.02) + 1;
        return gainSteps * decaySteps;
    }

    private List<GameplayCandidate> evaluateInitialTransitionGrid() {
        List<GameplayCandidate> candidates = new ArrayList<>();
        double[][] seededPoints = {
                {0.040, 1.16},
                {0.045, 1.22},
                {0.050, 1.28},
                {0.055, 1.34},
                {0.045, 1.28},
                {0.050, 1.22},
                {0.047, 1.30},
                {0.052, 1.26}
        };
        for (double[] point : seededPoints) {
            candidates.add(evaluateGameplayCandidate(point[0], point[1], activationDecay, readinessMemoryScale));
        }
        return candidates;
    }

    private List<GameplayCandidate> loadExistingCandidateDataset(File outDir) {
        List<GameplayCandidate> candidates = new ArrayList<>();
        File csv = new File(outDir, "gameplay_candidates.csv");
        if (!csv.exists()) {
            csv = new File(outDir, "candidate_phase_map.csv");
        }
        if (!csv.exists()) {
            return candidates;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csv))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 10) {
                    continue;
                }
                GameplayCandidate candidate = new GameplayCandidate(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]));
                candidate.persistence = Double.parseDouble(parts[4]);
                candidate.growth = Double.parseDouble(parts[5]);
                candidate.corridorStrength = Double.parseDouble(parts[6]);
                candidate.locality = Double.parseDouble(parts[7]);
                candidate.gameplayScore = Double.parseDouble(parts[8]);
                candidate.classification = parts[9].trim();
                candidates.add(candidate);
            }
        } catch (IOException e) {
            System.out.println("Unable to read previous gameplay dataset: " + e.getMessage());
        }
        return candidates;
    }

    private String formatCandidateKey(double gain, double distDecay) {
        return String.format(Locale.US, "%.4f|%.4f", gain, distDecay);
    }

    private double measurePersistence(ParameterSet params, double[][] distanceWeights) {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int cx = size / 2, cy = size / 2;
        activation[cy][cx] += 0.55;

        int persistence = 0;
        for (int t = 0; t < 300; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
            double peak = maxActivation(activation);
            if (peak >= 0.003) {
                persistence = t + 1;
            } else {
                break;
            }
        }
        return Math.max(0.0, Math.min(300.0, persistence));
    }

    private double measureGrowth(ParameterSet params, double[][] distanceWeights) {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int cx = size / 2, cy = size / 2;
        activation[cy][cx] += 0.55;
        for (int t = 0; t < 24; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
        }
        return measureActiveRadius(activation, size, 0.004);
    }

    private double measureCorridorStrength(ParameterSet params, double[][] distanceWeights) {
        int size = 55;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int ax = 12, ay = 20;
        int bx = 42, by = 34;
        double injection = 0.45;

        for (int t = 0; t < 35; t++) {
            activation[ay][ax] += injection;
            activation[by][bx] += injection;
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
        }
        return computeCorridorMean(activation, ax, ay, bx, by);
    }

    private double measureLocality(ParameterSet params, double[][] distanceWeights) {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int cx = size / 2, cy = size / 2;
        activation[cy][cx] += 0.55;
        for (int t = 0; t < 20; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
        }
        double radius = measureActiveRadius(activation, size, 0.004);
        return clamp(1.0 - Math.min(1.0, radius / 30.0), 0.0, 1.0);
    }

    private double computeGameplayScore(double persistence, double growth, double corridorStrength, double locality) {
        double persistenceScore = clamp((persistence - 120.0) / 180.0, 0.0, 1.0);
        double growthScore = clamp((growth - 1.0) / 4.0, 0.0, 1.0);
        double corridorScore = clamp((corridorStrength - 0.004) / 0.008, 0.0, 1.0);
        double localityScore = clamp((locality - 0.50) / 0.30, 0.0, 1.0);
        return clamp(0.30 * persistenceScore + 0.25 * growthScore + 0.25 * corridorScore + 0.20 * localityScore, 0.0, 1.0);
    }

    private String classifyCandidate(double persistence, double growth, double corridorStrength, double locality, double gameplayScore, double minArchetypeScore) {
        boolean runaway = gameplayScore < 0.10
                || (persistence > 400.0 && growth > 18.0)
                || (growth > 20.0 && corridorStrength > 0.025)
                || locality < 0.15;
        boolean dead = gameplayScore < 0.30
                || minArchetypeScore < 0.10
                || (persistence < 12.0 && growth < 1.0 && corridorStrength < 0.003);
        boolean bounded = gameplayScore >= 0.45
                && minArchetypeScore >= 0.30
                && growth <= 18.0
                && corridorStrength <= 0.020
                && locality >= 0.60;
        if (runaway) {
            return "RUNAWAY";
        }
        if (dead) {
            return "DEAD";
        }
        if (bounded) {
            return "PROMISING";
        }
        return "NEAR_BOUNDARY";
    }

    private void printProgressSummary(int completed, int totalPlanned, List<GameplayCandidate> allCandidates, GameplayCandidate current, boolean refined) {
        GameplayCandidate best = Collections.max(allCandidates, Comparator.comparingDouble(c -> c.gameplayScore));
        long dead = allCandidates.stream().filter(c -> "DEAD".equals(c.classification)).count();
        long nearBoundary = allCandidates.stream().filter(c -> "NEAR_BOUNDARY".equals(c.classification)).count();
        long promising = allCandidates.stream().filter(c -> "PROMISING".equals(c.classification)).count();
        long runaway = allCandidates.stream().filter(c -> "RUNAWAY".equals(c.classification)).count();

        System.out.println("# ==================================================");
        System.out.println(refined ? "Adaptive Refinement Progress" : "Initial Dataset Progress");
        System.out.println("Completed: " + completed + " / " + totalPlanned);
        System.out.printf(Locale.US, "Current neighborhood: gain=%.3f distance_decay=%.2f%n", current.transportGain, current.distanceDecay);
        System.out.printf(Locale.US, "Best gameplay score so far: %.3f (archetype floor=%.3f)%n", best.gameplayScore, best.minimumArchetypeScore);
        System.out.printf(Locale.US, "Best candidate: gain=%.3f distance_decay=%.2f persistence=%.0f growth=%.1f corridor=%.4f locality=%.2f classification=%s%n",
                best.transportGain, best.distanceDecay, best.persistence, best.growth, best.corridorStrength, best.locality, best.classification);
        System.out.println();
        System.out.println("Current classification counts");
        System.out.println("Dead: " + dead);
        System.out.println("Near boundary: " + nearBoundary);
        System.out.println("Promising: " + promising);
        System.out.println("Runaway: " + runaway);
        System.out.println("==================================================");

        if (best.gameplayScore > 0.0 && (best.classification.equals("PROMISING") || best.classification.equals("NEAR_BOUNDARY"))) {
            System.out.println("★★★★★ NEW BEST GAMEPLAY CANDIDATE ★★★★★");
            System.out.printf(Locale.US, "gain=%.3f distDecay=%.2f persistence=%.0f growth=%.1f corridor=%.4f locality=%.2f archetypeScore=%.4f minArchetype=%.4f score=%.4f classification=%s%n",
                    best.transportGain, best.distanceDecay, best.persistence, best.growth, best.corridorStrength, best.locality,
                    best.gameplayScore, best.minimumArchetypeScore, best.gameplayScore, best.classification);
        }
    }

    private OperatingPoint evaluatePoint(double gain, double distDecay, double decay, double readinessScale) {
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = gain;
        params.v3DistanceDecay = distDecay;
        params.v2ActivationDecay = decay;
        params.readinessMemoryScale = readinessScale;
        params.v2MemoryGain = 0.02;
        params.v2MemoryDecay = 0.001;
        params.v3FieldDepthGain = 0.01;
        params.v3FieldDepthDecay = 0.0001;
        params.v3FieldDepthRadius = 0.0;
        params.transportBalanceMode = "frozen";
        params.validationMaxHotspotRadius = 12.0;

        double[][] distanceWeights = precomputeDistanceWeights(DISTANCE_RADIUS, params.v3DistanceDecay);

        OperatingPoint op = new OperatingPoint(gain, distDecay, decay, readinessScale);
        op.miner = evaluateMiner(params, distanceWeights);
        op.builder = evaluateBuilder(params, distanceWeights);
        op.trader = evaluateTrader(params, distanceWeights);
        op.explorer = evaluateExplorer(params, distanceWeights);
        op.minerScore = scoreMiner(op.miner);
        op.builderScore = scoreBuilder(op.builder);
        op.traderScore = scoreTrader(op.trader);
        op.explorerScore = scoreExplorer(op.explorer);
        op.overallScore = (op.minerScore + op.builderScore + op.traderScore + op.explorerScore) / 4.0;
        op.minimumArchetypeScore = Math.min(Math.min(op.minerScore, op.builderScore), Math.min(op.traderScore, op.explorerScore));
        return op;
    }

    private double[][] precomputeDistanceWeights(int radius, double distanceDecay) {
        int size = radius * 2 + 1;
        double[][] weights = new double[size][size];
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dy == 0) {
                    weights[dy + radius][dx + radius] = 0.0;
                } else {
                    weights[dy + radius][dx + radius] = Math.exp(-Math.hypot(dx, dy) * distanceDecay);
                }
            }
        }
        return weights;
    }

    private MinerMetrics evaluateMiner(ParameterSet params, double[][] distanceWeights) {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int oldX = 12, oldY = 20;
        int newX = 28, newY = 20;
        double injection = 0.45;

        for (int t = 0; t < 40; t++) {
            activation[oldY][oldX] += injection;
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
        }
        for (int t = 0; t < 30; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
        }

        List<Double> oldTrace = new ArrayList<>();
        List<Double> newTrace = new ArrayList<>();
        int latency = 120;
        for (int t = 0; t < 120; t++) {
            activation[oldY][oldX] += injection;
            activation[newY][newX] += injection;
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
            double oldLocal = meanNearby(activation, oldX, oldY, 2);
            double newLocal = meanNearby(activation, newX, newY, 2);
            oldTrace.add(oldLocal);
            newTrace.add(newLocal);
            if (latency == 120 && oldLocal > newLocal + 0.005) {
                latency = t + 1;
            }
        }

        double oldFinal = average(oldTrace);
        double newFinal = average(newTrace);
        double advantage = newFinal > 0 ? oldFinal / newFinal : 0.0;
        double basinSize = countActiveRegionSize(activation, oldX, oldY, 3, 0.005);

        MinerMetrics metrics = new MinerMetrics();
        metrics.oldNewAdvantage = advantage;
        metrics.reopeningLatency = latency;
        metrics.persistentMiningBasin = basinSize;
        metrics.oldMemory = meanNearby(memory, oldX, oldY, 2);
        metrics.newMemory = meanNearby(memory, newX, newY, 2);
        metrics.oldFinalActivation = oldFinal;
        metrics.newFinalActivation = newFinal;
        return metrics;
    }

    private BuilderMetrics evaluateBuilder(ParameterSet params, double[][] distanceWeights) {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int cx = size / 2, cy = size / 2;
        double injection = 0.38;

        int peakArea = 0;
        for (int t = 0; t < 50; t++) {
            activation[cy][cx] += injection;
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
            peakArea = Math.max(peakArea, countActiveCells(activation, 0.005));
        }

        int lifetime = 0;
        for (int t = 0; t < 160; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
            int active = countActiveCells(activation, 0.005);
            if (active > 0) {
                lifetime = t + 1;
            }
        }
        int finalArea = countActiveCells(activation, 0.005);
        double stability = peakArea > 0 ? (double) finalArea / peakArea : 0.0;

        for (int t = 0; t < 10; t++) {
            activation[cy][cx] += injection;
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
        }
        double recovery = activation[cy][cx];

        BuilderMetrics metrics = new BuilderMetrics();
        metrics.settlementLifetime = lifetime;
        metrics.occupiedArea = finalArea;
        metrics.longTermStability = stability;
        metrics.recoveryAfterAbandonment = recovery;
        metrics.peakArea = peakArea;
        metrics.centerMemory = memory[cy][cx];
        return metrics;
    }

    private TraderMetrics evaluateTrader(ParameterSet params, double[][] distanceWeights) {
        int size = 55;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int ax = 12, ay = 18;
        int bx = 42, by = 36;
        double injection = 0.4;

        List<Double> midpointTrace = new ArrayList<>();
        double corridorSum = 0.0;
        int persistenceCount = 0;

        for (int t = 0; t < 40; t++) {
            activation[ay][ax] += injection;
            activation[by][bx] += injection;
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
            double corridor = computeCorridorMean(activation, ax, ay, bx, by);
            if (t >= 20) {
                midpointTrace.add(corridor);
                corridorSum += corridor;
                if (corridor > 0.012) persistenceCount++;
            }
        }

        double avgCorridor = midpointTrace.isEmpty() ? 0.0 : corridorSum / midpointTrace.size();
        double hubA = activation[ay][ax];
        double hubB = activation[by][bx];
        double avgHub = (hubA + hubB) / 2.0;
        double efficiency = avgHub > 0 ? avgCorridor / avgHub : 0.0;
        int pathActive = countActiveLine(activation, ax, ay, bx, by, 0.005);
        double routePersistence = midpointTrace.isEmpty() ? 0.0 : (double) persistenceCount / midpointTrace.size();

        TraderMetrics metrics = new TraderMetrics();
        metrics.corridorFormationScore = avgCorridor;
        metrics.transportEfficiency = efficiency;
        metrics.routePersistence = routePersistence;
        metrics.hubConnectivity = pathActive;
        metrics.hubAActivation = hubA;
        metrics.hubBActivation = hubB;
        return metrics;
    }

    private ExplorerMetrics evaluateExplorer(ParameterSet params, double[][] distanceWeights) {
        int size = 55;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        int cx = size / 2, cy = size / 2;
        double injection = 0.5;

        int lifetime = 0;
        double maxRadius = 0.0;
        double activeRegionsSum = 0.0;
        int sampleCount = 0;

        for (int t = 0; t < 180; t++) {
            if (t % 28 == 0) {
                int angle = t * 45;
                int radius = 14;
                int x = cx + (int) Math.round(radius * Math.cos(Math.toRadians(angle)));
                int y = cy + (int) Math.round(radius * Math.sin(Math.toRadians(angle)));
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    activation[y][x] += injection;
                }
            }
            stepSimulation(activation, memory, fieldDepth, params, size, distanceWeights, nextActivation, sourceReadiness);
            double radius = measureActiveRadius(activation, size, 0.005);
            if (radius > maxRadius) maxRadius = radius;
            if (radius > 10.0) lifetime++;
            if (t % 20 == 0) {
                activeRegionsSum += countConnectedComponents(activation, size, 0.005);
                sampleCount++;
            }
        }

        ExplorerMetrics metrics = new ExplorerMetrics();
        metrics.frontierRadius = maxRadius;
        metrics.activeFrontierLifetime = lifetime;
        metrics.activeRegionCount = sampleCount > 0 ? activeRegionsSum / sampleCount : 0.0;
        return metrics;
    }

    private double scoreMiner(MinerMetrics m) {
        double adv = clamp((m.oldNewAdvantage - 1.0) / 1.0, 0.0, 1.0);
        double latency = clamp(1.0 - (m.reopeningLatency - 1.0) / 119.0, 0.0, 1.0);
        double basin = clamp(m.persistentMiningBasin / 60.0, 0.0, 1.0);
        return (adv + latency + basin) / 3.0;
    }

    private double scoreBuilder(BuilderMetrics m) {
        double lifetime = clamp(m.settlementLifetime / 160.0, 0.0, 1.0);
        double area = clamp(m.occupiedArea / 140.0, 0.0, 1.0);
        double stability = clamp(m.longTermStability, 0.0, 1.0);
        double recovery = clamp(m.recoveryAfterAbandonment / 0.25, 0.0, 1.0);
        return (lifetime + area + stability + recovery) / 4.0;
    }

    private double scoreTrader(TraderMetrics m) {
        double corridor = clamp(m.corridorFormationScore / 0.12, 0.0, 1.0);
        double efficiency = clamp(m.transportEfficiency / 0.18, 0.0, 1.0);
        double persistence = clamp(m.routePersistence, 0.0, 1.0);
        double connectivity = clamp(m.hubConnectivity / 25.0, 0.0, 1.0);
        return (corridor + efficiency + persistence + connectivity) / 4.0;
    }

    private double scoreExplorer(ExplorerMetrics m) {
        double radius = clamp(m.frontierRadius / 20.0, 0.0, 1.0);
        double lifetime = clamp(m.activeFrontierLifetime / 180.0, 0.0, 1.0);
        double regions = clamp((m.activeRegionCount - 1.0) / 3.0, 0.0, 1.0);
        return (radius + lifetime + regions) / 3.0;
    }

    private void writeGameplayCandidatesCsv(List<GameplayCandidate> candidates, File file) throws IOException {
        List<GameplayCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());
        ranked.removeIf(candidate -> !"PROMISING".equals(candidate.classification) && !"NEAR_BOUNDARY".equals(candidate.classification));

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("gain,distDecay,activationDecay,readinessMemoryScale,persistence,growth,corridorStrength,locality,gameplayScore,classification");
            for (GameplayCandidate candidate : ranked) {
                writer.printf(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%.5f,%.3f,%.4f,%s%n",
                        candidate.transportGain, candidate.distanceDecay, candidate.activationDecay, candidate.readinessMemoryScale,
                        candidate.persistence, candidate.growth, candidate.corridorStrength, candidate.locality,
                        candidate.gameplayScore, candidate.classification);
            }
        }
    }

    private void writeGameplayScoresCsv(List<GameplayCandidate> candidates, File file) throws IOException {
        List<GameplayCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("gain,distDecay,activationDecay,readinessMemoryScale,persistence,growth,corridorStrength,locality,minerScore,builderScore,traderScore,explorerScore,minimumArchetypeScore,gameplayScore,classification");
            for (GameplayCandidate candidate : ranked) {
                writer.printf(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%.5f,%.3f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s%n",
                        candidate.transportGain, candidate.distanceDecay, candidate.activationDecay, candidate.readinessMemoryScale,
                        candidate.persistence, candidate.growth, candidate.corridorStrength, candidate.locality,
                        candidate.minerScore, candidate.builderScore, candidate.traderScore, candidate.explorerScore,
                        candidate.minimumArchetypeScore, candidate.gameplayScore, candidate.classification);
            }
        }
    }

    private void writeBoundaryRefinementCsv(List<GameplayCandidate> candidates, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("gain,distDecay,activationDecay,readinessMemoryScale,persistence,growth,corridorStrength,locality,gameplayScore,classification");
            for (GameplayCandidate candidate : candidates) {
                writer.printf(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%.5f,%.3f,%.4f,%s%n",
                        candidate.transportGain, candidate.distanceDecay, candidate.activationDecay, candidate.readinessMemoryScale,
                        candidate.persistence, candidate.growth, candidate.corridorStrength, candidate.locality,
                        candidate.gameplayScore, candidate.classification);
            }
        }
    }

    private void writeBestOperatingPoints(List<GameplayCandidate> candidates, File file) throws IOException {
        List<GameplayCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());
        ranked.removeIf(candidate -> !"PROMISING".equals(candidate.classification) && !"NEAR_BOUNDARY".equals(candidate.classification));

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("BEST GAMEPLAY OPERATING POINTS");
            writer.println("==============================");
            writer.println();
            for (int i = 0; i < Math.min(10, ranked.size()); i++) {
                GameplayCandidate candidate = ranked.get(i);
                writer.printf(Locale.US, "%d) gain=%.3f distDecay=%.3f persistence=%.0f growth=%.1f corridor=%.5f locality=%.3f miner=%.4f builder=%.4f trader=%.4f explorer=%.4f score=%.4f classification=%s%n",
                        i + 1, candidate.transportGain, candidate.distanceDecay, candidate.persistence,
                        candidate.growth, candidate.corridorStrength, candidate.locality,
                        candidate.minerScore, candidate.builderScore, candidate.traderScore, candidate.explorerScore,
                        candidate.gameplayScore, candidate.classification);
            }
        }
    }

    private void writeGameplayScoreDistribution(List<GameplayCandidate> candidates, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("bin,count");
            int[] bins = new int[20];
            for (GameplayCandidate candidate : candidates) {
                int index = (int) Math.min(bins.length - 1, Math.floor(candidate.gameplayScore * 20.0));
                bins[index]++;
            }
            for (int i = 0; i < bins.length; i++) {
                double lower = i / 20.0;
                double upper = (i + 1) / 20.0;
                writer.printf(Locale.US, "%.2f-%.2f,%d%n", lower, upper, bins[i]);
            }
        }
    }

    private void writeCandidatePhaseMap(List<GameplayCandidate> candidates, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("gain,distDecay,activationDecay,readinessMemoryScale,persistence,growth,corridorStrength,locality,gameplayScore,classification");
            for (GameplayCandidate candidate : candidates) {
                writer.printf(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%.5f,%.3f,%.4f,%s%n",
                        candidate.transportGain, candidate.distanceDecay, candidate.activationDecay, candidate.readinessMemoryScale,
                        candidate.persistence, candidate.growth, candidate.corridorStrength, candidate.locality,
                        candidate.gameplayScore, candidate.classification);
            }
        }
    }

    private void writeGameplayWindowReport(List<GameplayCandidate> candidates, File file) throws IOException {
        List<GameplayCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("GAMEPLAY WINDOW REPORT");
            writer.println("=====================");
            writer.println();
            writer.println("The refined window is centered on previously promising transition points and only explores local neighborhoods around them.");
            writer.println("The recommendation below is intended for Minecraft play rather than for abstract transport stability alone.");
            writer.println();
            writer.println("Top 10 gameplay candidates:");
            for (int i = 0; i < Math.min(10, ranked.size()); i++) {
                GameplayCandidate candidate = ranked.get(i);
                writer.printf(Locale.US, "%d) gain=%.3f distDecay=%.3f persistence=%.0f growth=%.1f corridor=%.5f locality=%.3f miner=%.4f builder=%.4f trader=%.4f explorer=%.4f score=%.4f classification=%s%n",
                        i + 1, candidate.transportGain, candidate.distanceDecay, candidate.persistence,
                        candidate.growth, candidate.corridorStrength, candidate.locality,
                        candidate.minerScore, candidate.builderScore, candidate.traderScore, candidate.explorerScore,
                        candidate.gameplayScore, candidate.classification);
            }
            writer.println();
            writer.println("Why the recommended point is suitable for Minecraft:");
            GameplayCandidate best = ranked.isEmpty() ? null : ranked.get(0);
            if (best != null) {
                writer.println("- The region stays active long enough for repeated play to leave visible traces.");
                writer.println("- Transport remains regional rather than becoming global saturation.");
                writer.println("- Repeated mining, building, and travel produce an evolving but bounded world.");
                writer.println("- Abandonment and recovery are still possible, so settlements can stabilize and later recover.");
                writer.println("- The point is a better gameplay fit than a purely transport-stable point because it balances persistence with recoverability.");
                writer.printf(Locale.US, "Recommended configuration: gain=%.3f distDecay=%.3f score=%.4f classification=%s%n",
                        best.transportGain, best.distanceDecay, best.gameplayScore, best.classification);
            }
        }
    }

    private void writeRecommendedReferenceConfiguration(List<GameplayCandidate> candidates, File file) throws IOException {
        List<GameplayCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((GameplayCandidate c) -> c.gameplayScore).reversed());
        GameplayCandidate best = ranked.isEmpty() ? null : ranked.get(0);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("RECOMMENDED REFERENCE CONFIGURATION");
            writer.println("=================================");
            writer.println();
            if (best == null) {
                writer.println("No candidate was evaluated.");
            } else {
                writer.printf(Locale.US, "transportGain=%.3f%n", best.transportGain);
                writer.printf(Locale.US, "distanceDecay=%.3f%n", best.distanceDecay);
                writer.printf(Locale.US, "activationDecay=%.3f%n", best.activationDecay);
                writer.printf(Locale.US, "readinessMemoryScale=%.3f%n", best.readinessMemoryScale);
                writer.printf(Locale.US, "classification=%s%n", best.classification);
                writer.printf(Locale.US, "gameplayScore=%.4f%n", best.gameplayScore);
                writer.printf(Locale.US, "minerScore=%.4f%n", best.minerScore);
                writer.printf(Locale.US, "builderScore=%.4f%n", best.builderScore);
                writer.printf(Locale.US, "traderScore=%.4f%n", best.traderScore);
                writer.printf(Locale.US, "explorerScore=%.4f%n", best.explorerScore);
                writer.println();
                writer.println("This setting is frozen as a reference point for behavioral simulation because it produces bounded regional evolution, visible history, and recoverable scars rather than immediate extinction or runaway growth.");
            }
        }
    }

    private void writeDiagram(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("GAMEPLAY PHASE DIAGRAM SUMMARY");
            writer.println("============================");
            writer.println();
            writer.println("Frozen transport architecture with bounded window sweep.");
            writer.println("Scores are normalized metrics across Miner, Builder, Trader, Explorer.");
            writer.println();

            List<OperatingPoint> sorted = new ArrayList<>(points);
            sorted.sort(Comparator.comparingDouble((OperatingPoint op) -> op.overallScore).reversed());
            OperatingPoint best = sorted.get(0);
            writer.println("Best operating point:");
            writer.printf(Locale.US,
                    "  transportGain=%.3f, distanceDecay=%.3f, activationDecay=%.3f, readinessMemoryScale=%.3f%n",
                    best.transportGain, best.distanceDecay, best.activationDecay, best.readinessMemoryScale);
            writer.printf(Locale.US, "  overallScore=%.4f, minimumArchetypeScore=%.4f%n", best.overallScore, best.minimumArchetypeScore);
            writer.println();

            writer.println("Top 5 candidates:");
            for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                OperatingPoint op = sorted.get(i);
                writer.printf(Locale.US,
                        "%d) gain=%.3f distDecay=%.3f actDecay=%.3f readiness=%.3f => score=%.4f (min=%.4f)\n",
                        i + 1, op.transportGain, op.distanceDecay, op.activationDecay, op.readinessMemoryScale,
                        op.overallScore, op.minimumArchetypeScore);
            }
            writer.println();

            writer.println("Score thresholds:");
            writer.println("  >=0.80 = excellent region candidate");
            writer.println("  0.65-0.79 = promising region");
            writer.println("  <0.65 = marginal for all archetypes");
            writer.println();

            writer.println("Archetype performance floor:");
            writer.printf("  best miner score = %.4f%n", sorted.stream().mapToDouble(p -> p.minerScore).max().orElse(0.0));
            writer.printf("  best builder score = %.4f%n", sorted.stream().mapToDouble(p -> p.builderScore).max().orElse(0.0));
            writer.printf("  best trader score = %.4f%n", sorted.stream().mapToDouble(p -> p.traderScore).max().orElse(0.0));
            writer.printf("  best explorer score = %.4f%n", sorted.stream().mapToDouble(p -> p.explorerScore).max().orElse(0.0));
            writer.println();

            writer.println("Distribution of best points by parameter:");
            writeParameterSummary(writer, points);
        }
    }

    private void writeOptimalRegion(List<OperatingPoint> points, File file) throws IOException {
        List<OperatingPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble((OperatingPoint op) -> op.overallScore).reversed());
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("OPTIMAL GAMEPLAY OPERATING REGION");
            writer.println("================================");
            writer.println();
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                OperatingPoint op = sorted.get(i);
                writer.printf(Locale.US, "Candidate %d:\n", i + 1);
                writePointDetails(writer, op);
                writer.println();
            }
        }
    }

    private void writeTradeoffReport(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("GAMEPLAY TRADEOFF REPORT");
            writer.println("========================");
            writer.println();
            writer.println("This report identifies which archetype constrains the bounded gameplay window and which parameter tradeoffs matter.");
            writer.println();

            writer.printf(Locale.US, "Best overall score in window: %.4f\n", points.stream().mapToDouble(p -> p.overallScore).max().orElse(0.0));
            writer.printf(Locale.US, "Best minimum archetype score in window: %.4f\n", points.stream().mapToDouble(p -> p.minimumArchetypeScore).max().orElse(0.0));
            writer.println();

            OperatingPoint best = Collections.max(points, Comparator.comparingDouble(p -> p.overallScore));
            writer.println("Best point summary:");
            writePointDetails(writer, best);
            writer.println();

            writer.println("Archetype limits across the window:");
            writer.printf(Locale.US, "  average miner score = %.4f\n", points.stream().mapToDouble(p -> p.minerScore).average().orElse(0.0));
            writer.printf(Locale.US, "  average builder score = %.4f\n", points.stream().mapToDouble(p -> p.builderScore).average().orElse(0.0));
            writer.printf(Locale.US, "  average trader score = %.4f\n", points.stream().mapToDouble(p -> p.traderScore).average().orElse(0.0));
            writer.printf(Locale.US, "  average explorer score = %.4f\n", points.stream().mapToDouble(p -> p.explorerScore).average().orElse(0.0));
            writer.println();

            writer.println("Parameter influence on overall score:");
            parameterInfluence(writer, points);
        }
    }

    private void writeRecommendedPoint(List<OperatingPoint> points, File file) throws IOException {
        OperatingPoint best = Collections.max(points, Comparator.comparingDouble(p -> p.overallScore));
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("RECOMMENDED MINECRAFT OPERATING POINT");
            writer.println("===================================");
            writer.println();
            writePointDetails(writer, best);
            writer.println();
            if (best.minimumArchetypeScore >= 0.70) {
                writer.println("Recommendation: This point is a strong candidate for direct Minecraft implementation within the frozen transport architecture.");
            } else if (best.minimumArchetypeScore >= 0.55) {
                writer.println("Recommendation: This point is promising but remains marginal for one or more archetypes.");
            } else {
                writer.println("Recommendation: No fully satisfactory gameplay operating point was found in the bounded window; this is the best available candidate.");
            }
        }
    }

    private void writePointDetails(PrintWriter writer, OperatingPoint op) {
        writer.printf(Locale.US, "  transportGain=%.3f, distanceDecay=%.3f, activationDecay=%.3f, readinessMemoryScale=%.3f%n",
                op.transportGain, op.distanceDecay, op.activationDecay, op.readinessMemoryScale);
        writer.printf(Locale.US, "  overallScore=%.4f, minArchetype=%.4f%n", op.overallScore, op.minimumArchetypeScore);
        writer.printf(Locale.US, "  minerScore=%.4f, builderScore=%.4f, traderScore=%.4f, explorerScore=%.4f%n",
                op.minerScore, op.builderScore, op.traderScore, op.explorerScore);
        writer.printf(Locale.US, "  miner(old/new advantage=%.3f, latency=%d, basin=%.1f)%n",
                op.miner.oldNewAdvantage, op.miner.reopeningLatency, op.miner.persistentMiningBasin);
        writer.printf(Locale.US, "  builder(lifetime=%d, area=%d, stability=%.3f, recovery=%.4f)%n",
                op.builder.settlementLifetime, op.builder.occupiedArea, op.builder.longTermStability, op.builder.recoveryAfterAbandonment);
        writer.printf(Locale.US, "  trader(corridor=%.4f, efficiency=%.4f, persistence=%.3f, connectivity=%d)%n",
                op.trader.corridorFormationScore, op.trader.transportEfficiency, op.trader.routePersistence, op.trader.hubConnectivity);
        writer.printf(Locale.US, "  explorer(radius=%.2f, lifetime=%d, regions=%.2f)%n",
                op.explorer.frontierRadius, op.explorer.activeFrontierLifetime, op.explorer.activeRegionCount);
    }

    private void writeParameterSummary(PrintWriter writer, List<OperatingPoint> points) {
        writer.println("  transportGain influence:");
        for (double gain : transportGains) {
            double mean = points.stream().filter(p -> p.transportGain == gain).mapToDouble(p -> p.overallScore).average().orElse(0.0);
            writer.printf(Locale.US, "    gain=%.3f => avgScore=%.4f%n", gain, mean);
        }
        writer.println("  distanceDecay influence:");
        for (double dist : distanceDecays) {
            double mean = points.stream().filter(p -> p.distanceDecay == dist).mapToDouble(p -> p.overallScore).average().orElse(0.0);
            writer.printf(Locale.US, "    distDecay=%.2f => avgScore=%.4f%n", dist, mean);
        }
        writer.printf(Locale.US, "  activationDecay influence: activationDecay=%.2f%n", activationDecay);
        writer.printf(Locale.US, "  readinessMemoryScale influence: readinessMemoryScale=%.2f%n", readinessMemoryScale);
    }

    private void parameterInfluence(PrintWriter writer, List<OperatingPoint> points) {
        double bestGain = points.stream().collect(java.util.stream.Collectors.groupingBy(p -> p.transportGain, java.util.stream.Collectors.averagingDouble(p -> p.overallScore)))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(0.0);
        double bestDist = points.stream().collect(java.util.stream.Collectors.groupingBy(p -> p.distanceDecay, java.util.stream.Collectors.averagingDouble(p -> p.overallScore)))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(0.0);
        double bestDecay = points.stream().collect(java.util.stream.Collectors.groupingBy(p -> p.activationDecay, java.util.stream.Collectors.averagingDouble(p -> p.overallScore)))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(0.0);
        double bestReadiness = points.stream().collect(java.util.stream.Collectors.groupingBy(p -> p.readinessMemoryScale, java.util.stream.Collectors.averagingDouble(p -> p.overallScore)))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(0.0);

        writer.printf(Locale.US, "  best overall average score by parameter: transportGain=%.3f, distanceDecay=%.2f, activationDecay=%.2f, readinessMemoryScale=%.2f%n",
                bestGain, bestDist, bestDecay, bestReadiness);
        writer.println("  Parameter trends are derived from mean overall score per value.");
    }

    private double maxActivation(double[][] grid) {
        double max = 0.0;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[0].length; x++) {
                max = Math.max(max, grid[y][x]);
            }
        }
        return max;
    }

    private double meanNearby(double[][] grid, int cx, int cy, int radius) {
        double sum = 0.0;
        int count = 0;
        int h = grid.length;
        int w = grid[0].length;
        for (int y = Math.max(0, cy - radius); y <= Math.min(h - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(w - 1, cx + radius); x++) {
                sum += grid[y][x];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private int countActiveCells(double[][] grid, double threshold) {
        int count = 0;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[0].length; x++) {
                if (grid[y][x] >= threshold) count++;
            }
        }
        return count;
    }

    private int countConnectedComponents(double[][] grid, int size, double threshold) {
        boolean[][] seen = new boolean[size][size];
        int count = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!seen[y][x] && grid[y][x] >= threshold) {
                    floodFill(grid, seen, x, y, size, threshold);
                    count++;
                }
            }
        }
        return count;
    }

    private void floodFill(double[][] grid, boolean[][] seen, int sx, int sy, int size, double threshold) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            if (x < 0 || x >= size || y < 0 || y >= size || seen[y][x] || grid[y][x] < threshold) continue;
            seen[y][x] = true;
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
    }

    private int countActiveRegionSize(double[][] grid, int cx, int cy, int radius, double threshold) {
        int count = 0;
        int h = grid.length;
        int w = grid[0].length;
        for (int y = Math.max(0, cy - radius); y <= Math.min(h - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(w - 1, cx + radius); x++) {
                if (grid[y][x] >= threshold) count++;
            }
        }
        return count;
    }

    private int countActiveLine(double[][] grid, int ax, int ay, int bx, int by, double threshold) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        int active = 0;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;
            int x = (int) Math.round(ax + t * (bx - ax));
            int y = (int) Math.round(ay + t * (by - ay));
            if (grid[y][x] >= threshold) active++;
        }
        return active;
    }

    private double computeCorridorMean(double[][] grid, int ax, int ay, int bx, int by) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        double sum = 0.0;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;
            int x = (int) Math.round(ax + t * (bx - ax));
            int y = (int) Math.round(ay + t * (by - ay));
            sum += grid[y][x];
        }
        return steps == 0 ? sum : sum / (steps + 1);
    }

    private double measureActiveRadius(double[][] activation, int size, double threshold) {
        int cx = size / 2, cy = size / 2;
        double max = 0.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (activation[y][x] >= threshold) {
                    max = Math.max(max, Math.hypot(x - cx, y - cy));
                }
            }
        }
        return max;
    }

    private void runRepresentativeDiagnostics(File outputDir) throws IOException {
        DiagnosticRun run = runSinglePointDiagnostic(0.053, 1.160, 0.45, 2.0);
        writeTickDiagnosticsCsv(run.tickDiagnostics, new File(outputDir, "tick_diagnostics.csv"));
        writeRecruitmentFailureBreakdownCsv(run.failureCounts, run.recruitmentFailures, new File(outputDir, "recruitment_failure_breakdown.csv"));
        writeActivationBudget(run, new File(outputDir, "activation_budget.txt"));
        writeSubsystemBottleneckReport(run, new File(outputDir, "subsystem_bottleneck_report.txt"));
    }

    private DiagnosticRun runSinglePointDiagnostic(double gain, double distDecay, double activationDecay, double readinessScale) {
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = gain;
        params.v3DistanceDecay = distDecay;
        params.v2ActivationDecay = activationDecay;
        params.readinessMemoryScale = readinessScale;
        params.v2MemoryGain = 0.02;
        params.v2MemoryDecay = 0.001;
        params.v3FieldDepthGain = 0.01;
        params.v3FieldDepthDecay = 0.0001;
        params.v3FieldDepthRadius = 0.0;
        params.transportBalanceMode = "frozen";
        params.validationMaxHotspotRadius = 12.0;

        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        double[][] distanceWeights = precomputeDistanceWeights(DISTANCE_RADIUS, params.v3DistanceDecay);

        DiagnosticRun run = new DiagnosticRun();
        run.failureCounts.put("insufficient readiness", 0L);
        run.failureCounts.put("insufficient neighboring activation", 0L);
        run.failureCounts.put("activation below threshold", 0L);
        run.failureCounts.put("memory below threshold", 0L);
        run.failureCounts.put("decay removed activation before recruitment", 0L);
        run.failureCounts.put("other", 0L);

        int cx = size / 2;
        int cy = size / 2;

        for (int tick = 0; tick < 300; tick++) {
            activation[cy][cx] += 0.55;
            run.totalCreated += 0.55;

            double decayedThisTick = 0.0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    decayedThisTick += activation[y][x] * params.v2ActivationDecay;
                }
            }

            long tickAttempts = 0L;
            long tickSuccesses = 0L;
            long tickFailures = 0L;
            long tickRecruitmentCreated = 0L;

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    sourceReadiness[y][x] = Math.exp(-memory[y][x] / params.readinessMemoryScale);
                }
            }

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double incoming = 0.0;
                    double destCap = 1.0 / (1.0 + 0.5 * activation[y][x]);
                    for (int dy = -DISTANCE_RADIUS; dy <= DISTANCE_RADIUS; dy++) {
                        for (int dx = -DISTANCE_RADIUS; dx <= DISTANCE_RADIUS; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                            double source = activation[ny][nx];
                            if (source <= 0.0) continue;
                            double readiness = sourceReadiness[ny][nx];
                            double distance = distanceWeights[dy + DISTANCE_RADIUS][dx + DISTANCE_RADIUS];
                            double transport = source * readiness * destCap * distance * params.v2TransportGain;
                            incoming += transport;
                        }
                    }
                    nextActivation[y][x] = activation[y][x] * (1.0 - params.v2ActivationDecay) + incoming;
                    run.totalTransported += incoming;
                }
            }

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    activation[y][x] = nextActivation[y][x];
                    if (activation[y][x] > 0.0) {
                        double stored = params.v2MemoryGain * activation[y][x];
                        memory[y][x] = Math.min(10.0, memory[y][x] + stored);
                        run.totalStored += stored;
                    }
                    memory[y][x] *= (1.0 - params.v2MemoryDecay);
                    fieldDepth[y][x] = fieldDepth[y][x] * (1.0 - params.v3FieldDepthDecay) + params.v3FieldDepthGain * Math.max(0.0, activation[y][x]);
                }
            }

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (activation[y][x] < 0.004) {
                        recordFailure(run.failureCounts, "decay removed activation before recruitment");
                        tickFailures++;
                        continue;
                    }
                    if (activation[y][x] < 0.008) {
                        recordFailure(run.failureCounts, "activation below threshold");
                        tickFailures++;
                        continue;
                    }
                    tickAttempts++;
                    if (memory[y][x] < 0.010) {
                        recordFailure(run.failureCounts, "memory below threshold");
                        tickFailures++;
                        continue;
                    }
                    if (sourceReadiness[y][x] < 0.45) {
                        recordFailure(run.failureCounts, "insufficient readiness");
                        tickFailures++;
                        continue;
                    }
                    boolean hasNeighbor = false;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                            if (activation[ny][nx] >= 0.004) {
                                hasNeighbor = true;
                                break;
                            }
                        }
                        if (hasNeighbor) break;
                    }
                    if (!hasNeighbor) {
                        recordFailure(run.failureCounts, "insufficient neighboring activation");
                        tickFailures++;
                        continue;
                    }
                    activation[y][x] += 0.035;
                    run.totalCreated += 0.035;
                    tickRecruitmentCreated++;
                    tickSuccesses++;
                }
            }

            run.recruitmentAttempts += tickAttempts;
            run.recruitmentSuccesses += tickSuccesses;
            run.recruitmentFailures += tickFailures;
            run.totalDecayed += decayedThisTick;
            run.totalReadinessRecovered += computeReadinessRecovered(memory, params.readinessMemoryScale);
            run.tickDiagnostics.add(new TickDiagnostic(tick + 1, run.totalCreated, run.totalTransported, run.totalDecayed, run.totalStored, run.totalReadinessRecovered, run.recruitmentAttempts, run.recruitmentSuccesses, run.recruitmentFailures));
        }

        return run;
    }

    private double computeReadinessRecovered(double[][] memory, double readinessScale) {
        double sum = 0.0;
        for (double[] row : memory) {
            for (double entry : row) {
                sum += 1.0 - Math.exp(-entry / readinessScale);
            }
        }
        return sum;
    }

    private void recordFailure(Map<String, Long> failureCounts, String reason) {
        failureCounts.put(reason, failureCounts.getOrDefault(reason, 0L) + 1L);
    }

    private void writeTickDiagnosticsCsv(List<TickDiagnostic> diagnostics, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("tick,created,transported,decayed,stored,readinessRecovered,recruitmentAttempts,recruitmentSuccesses,recruitmentFailures");
            for (TickDiagnostic diagnostic : diagnostics) {
                writer.printf(Locale.US, "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d%n",
                        diagnostic.tick, diagnostic.created, diagnostic.transported, diagnostic.decayed,
                        diagnostic.stored, diagnostic.readinessRecovered, diagnostic.recruitmentAttempts,
                        diagnostic.recruitmentSuccesses, diagnostic.recruitmentFailures);
            }
        }
    }

    private void writeRecruitmentFailureBreakdownCsv(Map<String, Long> failureCounts, long totalFailures, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("reason,count,share");
            for (Map.Entry<String, Long> entry : failureCounts.entrySet()) {
                if (entry.getValue() <= 0L) continue;
                double share = totalFailures <= 0L ? 0.0 : entry.getValue() / (double) totalFailures;
                writer.printf(Locale.US, "%s,%d,%.4f%n", entry.getKey(), entry.getValue(), share);
            }
        }
    }

    private void writeActivationBudget(DiagnosticRun run, File file) throws IOException {
        double created = Math.max(1.0, run.totalCreated);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("ACTIVATION BUDGET");
            writer.println("=================");
            writer.println();
            writer.printf(Locale.US, "Created: %.2f%n", run.totalCreated);
            writer.printf(Locale.US, "Transported: %.2f%% of created%n", run.totalTransported / created * 100.0);
            writer.printf(Locale.US, "Stored in memory: %.2f%% of created%n", run.totalStored / created * 100.0);
            writer.printf(Locale.US, "Recovered as readiness: %.2f%% of created%n", run.totalReadinessRecovered / created * 100.0);
            writer.printf(Locale.US, "Recruitment attempts: %d%n", run.recruitmentAttempts);
            writer.printf(Locale.US, "Recruitment successes: %d%n", run.recruitmentSuccesses);
            writer.printf(Locale.US, "Recruitment failures: %d%n", run.recruitmentFailures);
        }
    }

    private void writeSubsystemBottleneckReport(DiagnosticRun run, File file) throws IOException {
        String dominantReason = "other";
        long dominantCount = 0L;
        for (Map.Entry<String, Long> entry : run.failureCounts.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominantReason = entry.getKey();
                dominantCount = entry.getValue();
            }
        }
        double share = run.recruitmentFailures <= 0L ? 0.0 : dominantCount / (double) run.recruitmentFailures;
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("SUBSYSTEM BOTTLENECK REPORT");
            writer.println("===========================");
            writer.println();
            writer.printf(Locale.US, "Dominant recruitment failure: %s (%d failures, %.1f%% of failures)%n", dominantReason, dominantCount, share * 100.0);
            writer.println();
            writer.println("Interpretation:");
            if ("insufficient readiness".equals(dominantReason)) {
                writer.println("The principal bottleneck is the readiness gate. Activity is reaching the recruitment stage, but it is not being converted into stable recruitment because readiness falls below the threshold before a new site can be seeded.");
            } else if ("decay removed activation before recruitment".equals(dominantReason)) {
                writer.println("The principal bottleneck is activation lifetime. Activity is being lost to decay before it can participate in the downstream recruitment step.");
            } else if ("insufficient neighboring activation".equals(dominantReason)) {
                writer.println("The principal bottleneck is spatial support. Recruitment is being blocked because the local neighborhood is too sparse to sustain a new site.");
            } else if ("memory below threshold".equals(dominantReason)) {
                writer.println("The principal bottleneck is memory persistence. The system cannot sustain a recruitment event because the memory trace is too weak to support follow-on growth.");
            } else {
                writer.println("The principal bottleneck is the recruitment handoff itself. The system is failing to turn transient activation into a persistent gameplay event.");
            }
            writer.println();
            writer.printf(Locale.US, "Transported share of created activation: %.2f%%%n", run.totalTransported / Math.max(1.0, run.totalCreated) * 100.0);
            writer.printf(Locale.US, "Stored share of created activation: %.2f%%%n", run.totalStored / Math.max(1.0, run.totalCreated) * 100.0);
            writer.printf(Locale.US, "Recovered readiness share of created activation: %.2f%%%n", run.totalReadinessRecovered / Math.max(1.0, run.totalCreated) * 100.0);
        }
    }

    private double stepSimulation(double[][] activation, double[][] memory, double[][] fieldDepth, ParameterSet params, int size, double[][] distanceWeights, double[][] nextActivation, double[][] sourceReadiness) {
        double totalTransport = 0.0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                sourceReadiness[y][x] = Math.exp(-memory[y][x] / params.readinessMemoryScale);
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incoming = 0.0;
                double destCap = 1.0 / (1.0 + 0.5 * activation[y][x]);
                for (int dy = -DISTANCE_RADIUS; dy <= DISTANCE_RADIUS; dy++) {
                    for (int dx = -DISTANCE_RADIUS; dx <= DISTANCE_RADIUS; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                        double source = activation[ny][nx];
                        if (source <= 0.0) continue;
                        double readiness = sourceReadiness[ny][nx];
                        double distance = distanceWeights[dy + DISTANCE_RADIUS][dx + DISTANCE_RADIUS];
                        double transport = source * readiness * destCap * distance * params.v2TransportGain;
                        incoming += transport;
                    }
                }
                nextActivation[y][x] = activation[y][x] * (1.0 - params.v2ActivationDecay) + incoming;
                totalTransport += incoming;
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                activation[y][x] = nextActivation[y][x];
                if (activation[y][x] > 0.0) {
                    memory[y][x] = Math.min(10.0, memory[y][x] + params.v2MemoryGain * activation[y][x]);
                }
                memory[y][x] *= (1.0 - params.v2MemoryDecay);
                fieldDepth[y][x] = fieldDepth[y][x] * (1.0 - params.v3FieldDepthDecay) + params.v3FieldDepthGain * Math.max(0.0, activation[y][x]);
            }
        }

        return totalTransport;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class TickDiagnostic {
        int tick;
        double created;
        double transported;
        double decayed;
        double stored;
        double readinessRecovered;
        long recruitmentAttempts;
        long recruitmentSuccesses;
        long recruitmentFailures;

        TickDiagnostic(int tick, double created, double transported, double decayed, double stored,
                       double readinessRecovered, long recruitmentAttempts, long recruitmentSuccesses,
                       long recruitmentFailures) {
            this.tick = tick;
            this.created = created;
            this.transported = transported;
            this.decayed = decayed;
            this.stored = stored;
            this.readinessRecovered = readinessRecovered;
            this.recruitmentAttempts = recruitmentAttempts;
            this.recruitmentSuccesses = recruitmentSuccesses;
            this.recruitmentFailures = recruitmentFailures;
        }
    }

    private static class DiagnosticRun {
        double totalCreated;
        double totalTransported;
        double totalDecayed;
        double totalStored;
        double totalReadinessRecovered;
        long recruitmentAttempts;
        long recruitmentSuccesses;
        long recruitmentFailures;
        Map<String, Long> failureCounts = new LinkedHashMap<>();
        List<TickDiagnostic> tickDiagnostics = new ArrayList<>();
    }

    private static class GameplayCandidate {
        double transportGain;
        double distanceDecay;
        double activationDecay;
        double readinessMemoryScale;
        double persistence;
        double growth;
        double corridorStrength;
        double locality;
        double minerScore;
        double builderScore;
        double traderScore;
        double explorerScore;
        double minimumArchetypeScore;
        double gameplayScore;
        String classification;

        GameplayCandidate(double gain, double distDecay, double activationDecay, double readinessScale) {
            this.transportGain = gain;
            this.distanceDecay = distDecay;
            this.activationDecay = activationDecay;
            this.readinessMemoryScale = readinessScale;
        }
    }

    private static class OperatingPoint {
        double transportGain;
        double distanceDecay;
        double activationDecay;
        double readinessMemoryScale;
        MinerMetrics miner;
        BuilderMetrics builder;
        TraderMetrics trader;
        ExplorerMetrics explorer;
        double minerScore;
        double builderScore;
        double traderScore;
        double explorerScore;
        double overallScore;
        double minimumArchetypeScore;

        OperatingPoint(double gain, double distDecay, double activationDecay, double readinessScale) {
            this.transportGain = gain;
            this.distanceDecay = distDecay;
            this.activationDecay = activationDecay;
            this.readinessMemoryScale = readinessScale;
        }
    }

    private static class MinerMetrics {
        double oldNewAdvantage;
        int reopeningLatency;
        double persistentMiningBasin;
        double oldMemory;
        double newMemory;
        double oldFinalActivation;
        double newFinalActivation;
    }

    private static class BuilderMetrics {
        int settlementLifetime;
        int occupiedArea;
        double longTermStability;
        double recoveryAfterAbandonment;
        int peakArea;
        double centerMemory;
    }

    private static class TraderMetrics {
        double corridorFormationScore;
        double transportEfficiency;
        double routePersistence;
        int hubConnectivity;
        double hubAActivation;
        double hubBActivation;
    }

    private static class ExplorerMetrics {
        double frontierRadius;
        int activeFrontierLifetime;
        double activeRegionCount;
    }
}
