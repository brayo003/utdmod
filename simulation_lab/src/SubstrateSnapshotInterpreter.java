import java.util.Map;

public class SubstrateSnapshotInterpreter {
    
    // Thresholds (tuned from specification, not from transport equations)
    private static final double ACTIVATION_PERSIST_THRESHOLD = 0.010;
    private static final double MEMORY_PERSIST_THRESHOLD = 0.050;
    private static final double READINESS_HIGH_THRESHOLD = 0.60;
    private static final double TRANSPORT_CORRIDOR_THRESHOLD = 0.008;
    private static final double FIELD_DEPTH_SETTLED_THRESHOLD = 0.020;
    private static final double LOCAL_DENSITY_SETTLE_THRESHOLD = 0.15;
    private static final double GRADIENT_FRONTIER_THRESHOLD = 0.003;
    
    private static final int PERSISTENCE_TICKS = 3;
    private static final int LOCAL_RADIUS = 3;

    private double[][] activation;
    private double[][] memory;
    private double[][] fieldDepth;
    private double[][] transport;
    private int size;
    private int tick;

    public SubstrateSnapshotInterpreter(double[][] activation, double[][] memory, 
                                        double[][] fieldDepth, double[][] transport, int tick) {
        this.activation = activation;
        this.memory = memory;
        this.fieldDepth = fieldDepth;
        this.transport = transport;
        this.tick = tick;
        this.size = activation.length;
    }

    public GameplayIntent interpretRegion(int x, int y, Map<String, Integer> persistenceMap) {
        if (x < 0 || x >= size || y < 0 || y >= size) {
            return null;
        }

        // Read existing substrate state
        double act = activation[y][x];
        double mem = memory[y][x];
        double depth = fieldDepth[y][x];
        double readiness = Math.exp(-mem / 2.0); // exponential readiness from specification
        double trans = transport[y][x];
        
        // Compute local statistics
        double localGradient = computeLocalGradient(x, y);
        double localDensity = computeLocalDensity(x, y);
        double avgTransport = computeAverageTransport(x, y);
        double corridorStrength = computeCorridorStrength(x, y);

        String persistKey = x + "," + y;
        int persistCount = persistenceMap.getOrDefault(persistKey, 0);

        // Decision logic: thresholded on existing variables
        // Priority order: Settlement > Mining > Trade > Road > Combat > Exploration > Wandering

        // 1. Settlement: sustained activation + memory + field depth + density
        if (act >= ACTIVATION_PERSIST_THRESHOLD && 
            mem >= MEMORY_PERSIST_THRESHOLD && 
            depth >= FIELD_DEPTH_SETTLED_THRESHOLD &&
            localDensity >= LOCAL_DENSITY_SETTLE_THRESHOLD &&
            persistCount >= PERSISTENCE_TICKS) {
            return createIntent(x, y, GameplayIntent.Type.CREATE_SETTLEMENT, 
                               0.85, "Sustained activity + memory + field depth + density", 
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 2. Mining: sustained activation + high memory at a site
        if (act >= ACTIVATION_PERSIST_THRESHOLD && 
            mem >= MEMORY_PERSIST_THRESHOLD * 2.0 &&
            persistCount >= PERSISTENCE_TICKS) {
            return createIntent(x, y, GameplayIntent.Type.CONTINUE_MINING,
                               0.80, "Sustained extraction: high activation + strong memory",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 3. Trade: high corridor transport between stable regions
        if (act >= ACTIVATION_PERSIST_THRESHOLD && 
            corridorStrength >= TRANSPORT_CORRIDOR_THRESHOLD * 2.0 &&
            mem >= MEMORY_PERSIST_THRESHOLD &&
            persistCount >= PERSISTENCE_TICKS) {
            return createIntent(x, y, GameplayIntent.Type.INITIATE_TRADE,
                               0.75, "Strong bidirectional transport + stable nodes",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 4. Road: sustained corridor transport
        if (corridorStrength >= TRANSPORT_CORRIDOR_THRESHOLD &&
            act >= ACTIVATION_PERSIST_THRESHOLD * 0.5 &&
            persistCount >= PERSISTENCE_TICKS) {
            return createIntent(x, y, GameplayIntent.Type.CREATE_ROAD,
                               0.70, "Persistent transport corridor detected",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 5. Combat: high gradient + opposed activation (frontier pressure)
        if (act >= ACTIVATION_PERSIST_THRESHOLD && 
            localGradient >= GRADIENT_FRONTIER_THRESHOLD &&
            avgTransport > trans * 0.5 &&
            persistCount >= 2) {
            return createIntent(x, y, GameplayIntent.Type.ENGAGE_COMBAT,
                               0.60, "Frontier pressure: strong gradient + local gradient",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 6. Territory Expansion: high readiness + rising frontier gradient
        if (act >= ACTIVATION_PERSIST_THRESHOLD && 
            readiness >= READINESS_HIGH_THRESHOLD &&
            localGradient >= GRADIENT_FRONTIER_THRESHOLD * 0.5 &&
            mem >= MEMORY_PERSIST_THRESHOLD &&
            persistCount >= PERSISTENCE_TICKS) {
            return createIntent(x, y, GameplayIntent.Type.EXPAND_TERRITORY,
                               0.70, "Frontier expansion: high readiness + gradient",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 7. Exploration: rising transport gradient (frontier)
        if (act >= ACTIVATION_PERSIST_THRESHOLD * 0.3 &&
            localGradient >= GRADIENT_FRONTIER_THRESHOLD * 0.3 &&
            persistCount >= 2) {
            return createIntent(x, y, GameplayIntent.Type.EXPLORE,
                               0.50, "Frontier exploration: local gradient detected",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        // 8. Wandering: weak transient activation
        if (act >= ACTIVATION_PERSIST_THRESHOLD * 0.2 &&
            act < ACTIVATION_PERSIST_THRESHOLD &&
            trans > 0.0) {
            return createIntent(x, y, GameplayIntent.Type.WANDER,
                               0.30, "Weak transient movement",
                               act, mem, readiness, trans, depth, localGradient, localDensity);
        }

        return null;
    }

    private double computeLocalGradient(int x, int y) {
        double maxGrad = 0.0;
        for (int dy = -LOCAL_RADIUS; dy <= LOCAL_RADIUS; dy++) {
            for (int dx = -LOCAL_RADIUS; dx <= LOCAL_RADIUS; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                    double diff = Math.abs(activation[y][x] - activation[ny][nx]);
                    maxGrad = Math.max(maxGrad, diff);
                }
            }
        }
        return maxGrad;
    }

    private double computeLocalDensity(int x, int y) {
        int count = 0;
        double total = 0.0;
        for (int dy = -LOCAL_RADIUS; dy <= LOCAL_RADIUS; dy++) {
            for (int dx = -LOCAL_RADIUS; dx <= LOCAL_RADIUS; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                    if (activation[ny][nx] >= ACTIVATION_PERSIST_THRESHOLD) {
                        count++;
                    }
                    total++;
                }
            }
        }
        return total > 0.0 ? count / total : 0.0;
    }

    private double computeAverageTransport(int x, int y) {
        double sum = 0.0;
        int count = 0;
        for (int dy = -LOCAL_RADIUS; dy <= LOCAL_RADIUS; dy++) {
            for (int dx = -LOCAL_RADIUS; dx <= LOCAL_RADIUS; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                    sum += transport[ny][nx];
                    count++;
                }
            }
        }
        return count > 0.0 ? sum / count : 0.0;
    }

    private double computeCorridorStrength(int x, int y) {
        // Bidirectional transport: how much transport flows through this cell from neighbors
        double incoming = 0.0, outgoing = 0.0;
        for (int dy = -LOCAL_RADIUS; dy <= LOCAL_RADIUS; dy++) {
            for (int dx = -LOCAL_RADIUS; dx <= LOCAL_RADIUS; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                    // Incoming: from neighbors toward this cell
                    incoming += Math.max(0.0, transport[ny][nx] - transport[y][x]);
                    // Outgoing: from this cell toward neighbors
                    outgoing += Math.max(0.0, transport[y][x] - transport[ny][nx]);
                }
            }
        }
        return (incoming + outgoing) / Math.max(1.0, (2.0 * LOCAL_RADIUS + 1.0) * (2.0 * LOCAL_RADIUS + 1.0) - 1.0);
    }

    private GameplayIntent createIntent(int x, int y, GameplayIntent.Type type, 
                                        double confidence, String reason,
                                        double act, double mem, double readiness, 
                                        double trans, double depth,
                                        double localGrad, double localDen) {
        GameplayIntent intent = new GameplayIntent(tick, x, y, type, confidence, reason);
        intent.activation = act;
        intent.memory = mem;
        intent.readiness = readiness;
        intent.transport = trans;
        intent.fieldDepth = depth;
        intent.localGradient = localGrad;
        intent.localDensity = localDen;
        return intent;
    }
}
