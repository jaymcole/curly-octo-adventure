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

    public abstract void act();

    public abstract BaseSnail createCopy();

    public boolean isDone() {
        return complete;
    }

    protected void markTileAsPartOfMap(BaseSnail snail) {
        this.map.touchTile(snail.coordinate);
    }

}
