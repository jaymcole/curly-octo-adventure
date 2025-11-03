package curly.octo.common.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.map.enums.Direction;

/**
 * Represents a potential expansion point where new snails can spawn.
 * Used to create complex, interconnected dungeon layouts.
 */
public class ExpansionNode {

    public enum Priority {
        NECESSARY,  // Must be filled (e.g., end of hallway to avoid dead ends)
        OPTIONAL    // May be filled if map needs more complexity
    }

    private final Vector3 position;
    private final Direction direction;
    private final Priority priority;
    private final String sourceSnailType; // For debugging
    private boolean consumed = false;

    public ExpansionNode(Vector3 position, Direction direction, Priority priority, String sourceSnailType) {
        this.position = position.cpy();
        this.direction = direction;
        this.priority = priority;
        this.sourceSnailType = sourceSnailType;
    }

    public Vector3 getPosition() {
        return position.cpy();
    }

    public Direction getDirection() {
        return direction;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getSourceSnailType() {
        return sourceSnailType;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void consume() {
        this.consumed = true;
    }

    @Override
    public String toString() {
        return String.format("ExpansionNode{pos=%s, dir=%s, priority=%s, source=%s, consumed=%s}",
                           position, direction, priority, sourceSnailType, consumed);
    }
}
