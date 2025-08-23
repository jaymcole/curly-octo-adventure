package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.Turn;

import java.util.Random;

/**
 * A snail that changes direction by specified angle (90-degree increments).
 * Completes immediately after turning.
 */
public class TurnSnail extends BaseSnail {

    private final Turn turnDirection; // 90, 180, -90, etc.
    private boolean turned = false;

    public TurnSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, Turn turnDirection) {
        super(map, coordinate, direction, random);
        this.turnDirection = turnDirection;
    }

    @Override
    protected SnailResult doStep() {
        if (turned) {
            complete = true;
            return SnailResult.COMPLETE;
        }

        this.direction = Direction.rotate(direction, turnDirection);

        turned = true;
        complete = true;
        return SnailResult.COMPLETE;
    }

    @Override
    public BaseSnail createCopy() {
        TurnSnail copy = new TurnSnail(map, coordinate.cpy(), direction, random, turnDirection);
        copy.turned = this.turned;
        return copy;
    }
}
