package curly.octo.map.generators.snails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a snail execution, containing completion status and any spawned snails.
 */
public class SnailResult {
    
    public static final SnailResult CONTINUE = new SnailResult(false, Collections.emptyList());
    public static final SnailResult COMPLETE = new SnailResult(true, Collections.emptyList());
    
    private final boolean complete;
    private final List<BaseSnail> spawnedSnails;
    
    public SnailResult(boolean complete, List<BaseSnail> spawnedSnails) {
        this.complete = complete;
        this.spawnedSnails = new ArrayList<>(spawnedSnails);
    }
    
    public static SnailResult spawn(BaseSnail... snails) {
        List<BaseSnail> spawned = new ArrayList<>();
        for (BaseSnail snail : snails) {
            spawned.add(snail);
        }
        return new SnailResult(true, spawned);
    }
    
    public static SnailResult continueAndSpawn(BaseSnail... snails) {
        List<BaseSnail> spawned = new ArrayList<>();
        for (BaseSnail snail : snails) {
            spawned.add(snail);
        }
        return new SnailResult(false, spawned);
    }
    
    public boolean isComplete() {
        return complete;
    }
    
    public List<BaseSnail> getSpawnedSnails() {
        return new ArrayList<>(spawnedSnails);
    }
}