package curly.octo.common.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.enums.Direction;

import java.util.Random;

/**
 * A snail that executes one snail, then executes another when the first completes.
 * Enables fluent chaining: snail1.then(snail2)
 */
public class SequentialSnail extends BaseSnail {

    private BaseSnail currentSnail;
    private BaseSnail nextSnail;
    private boolean onSecondSnail = false;

    public SequentialSnail(GameMap map, Vector3 coordinate, Direction direction, Random random,
                          BaseSnail first, BaseSnail second) {
        super(map, coordinate, direction, random);
        this.currentSnail = first.createCopy();
        this.nextSnail = second.createCopy();

        // Set initial snail to our position
        this.currentSnail.coordinate = coordinate.cpy();
        this.currentSnail.direction = direction;
    }

    @Override
    protected SnailResult doStep() {
        if (complete) {
            return SnailResult.COMPLETE;
        }

        SnailResult result = currentSnail.execute();

        // Update our position to match current snail
        this.coordinate = currentSnail.coordinate.cpy();
        this.direction = currentSnail.direction;

        if (result.isComplete()) {
            if (!onSecondSnail) {
                // Switch to second snail
                onSecondSnail = true;
                nextSnail.coordinate = this.coordinate.cpy();
                nextSnail.direction = this.direction;
                currentSnail = nextSnail;

                // Execute first step of second snail immediately
                return currentSnail.execute();
            } else {
                // Both snails complete
                complete = true;
                return SnailResult.COMPLETE;
            }
        }

        // Forward any spawned snails
        return result;
    }

    @Override
    public BaseSnail createCopy() {
        SequentialSnail copy = new SequentialSnail(map, coordinate.cpy(), direction, random,
                                                  currentSnail.createCopy(), nextSnail.createCopy());
        copy.onSecondSnail = this.onSecondSnail;
        return copy;
    }
}
