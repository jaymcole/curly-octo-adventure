package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.Random;

public abstract class BaseSnail {

    public final GameMap map;

    protected Vector3 coordinate;
    protected Direction direction;
    protected boolean complete;
    protected Random random;

    public BaseSnail(GameMap map, Vector3 coordinate, Direction direction, Random random) {
        this.map = map;
        this.coordinate = coordinate;
        this.direction = direction;
        this.complete = false;
        this.random = random;
    }

    /**
     * Execute one step of this snail's behavior.
     * @return SnailResult indicating completion status and any spawned snails
     */
    public SnailResult execute() {
        if (complete) {
            return SnailResult.COMPLETE;
        }
        
        return doStep();
    }

    /**
     * Implement the specific behavior for this snail type.
     * @return SnailResult with completion and spawn information
     */
    protected abstract SnailResult doStep();

    /**
     * Create a copy of this snail at the current position.
     */
    public abstract BaseSnail createCopy();

    public boolean isDone() {
        return complete;
    }

    protected void markTileAsPartOfMap() {
        this.map.touchTile(this.coordinate);
    }
    
    protected void markTileAsPartOfMap(Vector3 coordinate) {
        this.map.touchTile(coordinate);
    }

    // Getters for snail state
    public Vector3 getCoordinate() {
        return coordinate.cpy();
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    // Fluent API for chaining
    public SequentialSnail then(BaseSnail nextSnail) {
        return new SequentialSnail(map, coordinate.cpy(), direction, random, this, nextSnail);
    }
    
    public ParallelSnail spawn(BaseSnail... snails) {
        return new ParallelSnail(map, coordinate.cpy(), direction, random, this, snails);
    }

}
