package curly.octo.map.generators.snails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a snail execution, containing completion status, spawned snails, and expansion nodes.
 */
public class SnailResult {
    
    public static final SnailResult CONTINUE = new SnailResult(false, Collections.emptyList(), Collections.emptyList());
    public static final SnailResult COMPLETE = new SnailResult(true, Collections.emptyList(), Collections.emptyList());
    
    private final boolean complete;
    private final List<BaseSnail> spawnedSnails;
    private final List<ExpansionNode> expansionNodes;
    
    public SnailResult(boolean complete, List<BaseSnail> spawnedSnails) {
        this.complete = complete;
        this.spawnedSnails = new ArrayList<>(spawnedSnails);
        this.expansionNodes = new ArrayList<>();
    }
    
    public SnailResult(boolean complete, List<BaseSnail> spawnedSnails, List<ExpansionNode> expansionNodes) {
        this.complete = complete;
        this.spawnedSnails = new ArrayList<>(spawnedSnails);
        this.expansionNodes = new ArrayList<>(expansionNodes);
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
    
    public static SnailResult withExpansionNodes(boolean complete, ExpansionNode... nodes) {
        List<ExpansionNode> nodeList = new ArrayList<>();
        for (ExpansionNode node : nodes) {
            nodeList.add(node);
        }
        return new SnailResult(complete, Collections.emptyList(), nodeList);
    }
    
    public boolean isComplete() {
        return complete;
    }
    
    public List<BaseSnail> getSpawnedSnails() {
        return new ArrayList<>(spawnedSnails);
    }
    
    public List<ExpansionNode> getExpansionNodes() {
        return new ArrayList<>(expansionNodes);
    }
}