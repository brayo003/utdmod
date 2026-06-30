public class GameplayIntent {
    public enum Type {
        EXPLORE,
        WANDER,
        BEGIN_MINING,
        CONTINUE_MINING,
        ABANDON_MINE,
        ENGAGE_COMBAT,
        CRAFT,
        CREATE_SETTLEMENT,
        CREATE_ROAD,
        INITIATE_TRADE,
        EXPAND_TERRITORY
    }

    public int tick;
    public int x, y;
    public Type type;
    public double confidence;
    public String reason;
    
    // Supporting substrate values for diagnostics
    public double activation;
    public double memory;
    public double readiness;
    public double transport;
    public double fieldDepth;
    public double localGradient;
    public double localDensity;

    public GameplayIntent(int tick, int x, int y, Type type, double confidence, String reason) {
        this.tick = tick;
        this.x = x;
        this.y = y;
        this.type = type;
        this.confidence = confidence;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return String.format("Tick %d @ (%d,%d): %s (conf=%.3f) - %s", tick, x, y, type, confidence, reason);
    }
}
