import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;

public class GameplayInterpreterIntegrationTest {

    @Test
    void runGameplayInterpreterOnExistingSimulation() throws IOException {
        File outputDir = new File("run/gameplay_interpreter");
        if (!outputDir.exists()) outputDir.mkdirs();

        // Configure parameters
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = 0.059;
        params.v3DistanceDecay = 1.040;
        params.v2ActivationDecay = 0.45;
        params.readinessMemoryScale = 2.0;
        params.v2MemoryGain = 0.02;
        params.v2MemoryDecay = 0.001;
        params.v3FieldDepthGain = 0.01;
        params.v3FieldDepthDecay = 0.0001;
        params.v3FieldDepthRadius = 0.0;
        params.transportBalanceMode = "frozen";

        // Simulate a settlement growth scenario
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] nextActivation = new double[size][size];
        double[][] sourceReadiness = new double[size][size];
        double[][] distanceWeights = precomputeDistanceWeights(15, params.v3DistanceDecay);

        int cx = size / 2, cy = size / 2;
        double injectionStrength = 0.55;
        int totalTicks = 300;

        List<GameplayIntent> allIntents = new ArrayList<>();
        BehaviorSelector selector = new BehaviorSelector();

        for (int tick = 0; tick < totalTicks; tick++) {
            // Inject activity at center
            activation[cy][cx] += injectionStrength;

            // Step the simulation
            stepSimulation(activation, memory, fieldDepth, params, size, nextActivation, sourceReadiness);

            // Create transport field for interpreter
            double[][] transport = computeTransport(activation, memory, params, size);

            // Interpret gameplay intents
            SubstrateSnapshotInterpreter interpreter = new SubstrateSnapshotInterpreter(
                activation, memory, fieldDepth, transport, tick
            );

            List<GameplayIntent> candidates = new ArrayList<>();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    GameplayIntent intent = interpreter.interpretRegion(x, y, selector.getPersistenceMap());
                    if (intent != null) {
                        candidates.add(intent);
                    }
                }
            }

            // Select best intents (one per region per tick)
            List<GameplayIntent> selected = selector.selectIntents(candidates);
            allIntents.addAll(selected);

            // Feedback: emit intents don't modify substrate directly; just record them
            // The substrate evolves naturally and gameplay is interpreted from it
        }

        // Write output files
        writeGameplayEventsCSV(allIntents, new File(outputDir, "gameplay_events.csv"));
        writeGameplayStatistics(allIntents, new File(outputDir, "gameplay_event_statistics.csv"));
        writeGameplayTimeline(allIntents, new File(outputDir, "gameplay_event_timeline.csv"));

        System.out.println("Gameplay interpreter test complete.");
        System.out.println("Total intents emitted: " + allIntents.size());
        System.out.println("Output: " + outputDir.getAbsolutePath());
    }

    private double[][] computeTransport(double[][] activation, double[][] memory, ParameterSet params, int size) {
        double[][] transport = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incomingTransport = 0.0;
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                            double source = activation[ny][nx];
                            if (source > 0.0) {
                                double readiness = Math.exp(-memory[ny][nx] / params.readinessMemoryScale);
                                double destCap = 1.0 / (1.0 + 0.5 * activation[y][x]);
                                double distance = Math.exp(-Math.sqrt(dy*dy + dx*dx) / 1.0);
                                incomingTransport += source * readiness * destCap * distance * params.v2TransportGain;
                            }
                        }
                    }
                }
                transport[y][x] = incomingTransport;
            }
        }
        return transport;
    }

    private void stepSimulation(double[][] activation, double[][] memory, double[][] fieldDepth, 
                               ParameterSet params, int size,
                               double[][] nextActivation, double[][] sourceReadiness) {
        // Readiness computation
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                sourceReadiness[y][x] = Math.exp(-memory[y][x] / params.readinessMemoryScale);
            }
        }

        // Transport and activation update
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incoming = 0.0;
                double destCap = 1.0 / (1.0 + 0.5 * activation[y][x]);
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                            double source = activation[ny][nx];
                            if (source > 0.0) {
                                double readiness = sourceReadiness[ny][nx];
                                double distance = Math.exp(-Math.sqrt(dy*dy + dx*dx) / 1.0);
                                double transport = source * readiness * destCap * distance * params.v2TransportGain;
                                incoming += transport;
                            }
                        }
                    }
                }
                nextActivation[y][x] = activation[y][x] * (1.0 - params.v2ActivationDecay) + incoming;
            }
        }

        // Update memory and field depth
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                activation[y][x] = nextActivation[y][x];
                if (activation[y][x] > 0.0) {
                    double stored = params.v2MemoryGain * activation[y][x];
                    memory[y][x] = Math.min(10.0, memory[y][x] + stored);
                }
                memory[y][x] *= (1.0 - params.v2MemoryDecay);
                fieldDepth[y][x] = fieldDepth[y][x] * (1.0 - params.v3FieldDepthDecay) + 
                                  params.v3FieldDepthGain * Math.max(0.0, activation[y][x]);
            }
        }
    }

    private double[][] precomputeDistanceWeights(int radius, double decayFactor) {
        double[][] weights = new double[2*radius+1][2*radius+1];
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double dist = Math.sqrt(dy*dy + dx*dx);
                weights[dy+radius][dx+radius] = Math.exp(-dist / decayFactor);
            }
        }
        return weights;
    }

    private void writeGameplayEventsCSV(List<GameplayIntent> intents, File file) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("tick,x,y,type,confidence,reason,activation,memory,readiness,transport,fieldDepth,localGradient,localDensity");
            for (GameplayIntent intent : intents) {
                w.printf("%d,%d,%d,%s,%.4f,\"%s\",%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                    intent.tick, intent.x, intent.y, intent.type, intent.confidence, intent.reason,
                    intent.activation, intent.memory, intent.readiness, intent.transport, 
                    intent.fieldDepth, intent.localGradient, intent.localDensity);
            }
        }
    }

    private void writeGameplayStatistics(List<GameplayIntent> intents, File file) throws IOException {
        Map<GameplayIntent.Type, Integer> counts = new HashMap<>();
        Map<GameplayIntent.Type, Double> avgConfidence = new HashMap<>();

        for (GameplayIntent intent : intents) {
            counts.put(intent.type, counts.getOrDefault(intent.type, 0) + 1);
            avgConfidence.put(intent.type, avgConfidence.getOrDefault(intent.type, 0.0) + intent.confidence);
        }

        for (GameplayIntent.Type type : avgConfidence.keySet()) {
            avgConfidence.put(type, avgConfidence.get(type) / counts.get(type));
        }

        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("Type,Count,AvgConfidence,Percentage");
            int total = intents.size();
            for (GameplayIntent.Type type : counts.keySet()) {
                int count = counts.get(type);
                double pct = (count * 100.0) / Math.max(1, total);
                w.printf("%s,%d,%.4f,%.2f%%%n", type, count, avgConfidence.get(type), pct);
            }
        }
    }

    private void writeGameplayTimeline(List<GameplayIntent> intents, File file) throws IOException {
        Map<Integer, List<GameplayIntent>> byTick = new LinkedHashMap<>();
        for (GameplayIntent intent : intents) {
            byTick.putIfAbsent(intent.tick, new ArrayList<>());
            byTick.get(intent.tick).add(intent);
        }

        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("Tick,Event,Position,Confidence");
            for (int tick : byTick.keySet()) {
                for (GameplayIntent intent : byTick.get(tick)) {
                    w.printf("%d,%s,(%d:%d),%.3f%n", tick, intent.type, intent.x, intent.y, intent.confidence);
                }
            }
        }
    }
}
