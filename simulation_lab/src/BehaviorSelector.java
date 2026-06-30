import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BehaviorSelector {
    
    // Deterministic priority order (higher index = lower priority)
    private static final GameplayIntent.Type[] PRIORITY_ORDER = {
        GameplayIntent.Type.CREATE_SETTLEMENT,
        GameplayIntent.Type.CONTINUE_MINING,
        GameplayIntent.Type.INITIATE_TRADE,
        GameplayIntent.Type.CREATE_ROAD,
        GameplayIntent.Type.ENGAGE_COMBAT,
        GameplayIntent.Type.EXPAND_TERRITORY,
        GameplayIntent.Type.EXPLORE,
        GameplayIntent.Type.WANDER
    };

    private Map<String, Integer> persistenceMap;
    private Map<String, GameplayIntent> lastIntents;

    public BehaviorSelector() {
        this.persistenceMap = new HashMap<>();
        this.lastIntents = new HashMap<>();
    }

    public List<GameplayIntent> selectIntents(List<GameplayIntent> candidates) {
        List<GameplayIntent> selected = new ArrayList<>();
        
        // Group by region
        Map<String, List<GameplayIntent>> byRegion = new HashMap<>();
        for (GameplayIntent intent : candidates) {
            String key = intent.x + "," + intent.y;
            byRegion.putIfAbsent(key, new ArrayList<>());
            byRegion.get(key).add(intent);
        }

        // Select highest-priority intent per region
        for (String regionKey : byRegion.keySet()) {
            List<GameplayIntent> regionIntents = byRegion.get(regionKey);
            
            // Sort by priority
            regionIntents.sort((a, b) -> {
                int prioA = getPriority(a.type);
                int prioB = getPriority(b.type);
                return Integer.compare(prioA, prioB); // Higher priority (lower index) first
            });

            GameplayIntent best = regionIntents.get(0);
            
            // Update persistence count
            int count = persistenceMap.getOrDefault(regionKey, 0);
            GameplayIntent last = lastIntents.get(regionKey);
            
            if (last != null && last.type.equals(best.type)) {
                // Same type continues: increment persistence
                count++;
            } else {
                // New type: reset persistence
                count = 1;
            }
            
            persistenceMap.put(regionKey, count);
            lastIntents.put(regionKey, best);
            selected.add(best);
        }

        return selected;
    }

    public Map<String, Integer> getPersistenceMap() {
        return persistenceMap;
    }

    private int getPriority(GameplayIntent.Type type) {
        for (int i = 0; i < PRIORITY_ORDER.length; i++) {
            if (PRIORITY_ORDER[i].equals(type)) {
                return i;
            }
        }
        return Integer.MAX_VALUE; // Unknown type, lowest priority
    }
}
