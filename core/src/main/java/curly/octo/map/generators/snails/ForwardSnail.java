package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.Random;

public class ForwardSnail extends BaseSnail{

    public int distance;

    public ForwardSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int distance) {
        super(map, coordinate, direction, random);
        this.distance = distance;
    }

    @Override
    public void act() {
        if (distance > 0) {
            Direction.advanceVector(direction, coordinate);
        } else {
            complete = true;
        }
        distance--;
    }

    @Override
    public BaseSnail createCopy() {
        return new ForwardSnail(map, coordinate.cpy(), direction, random, distance);
    }

}
