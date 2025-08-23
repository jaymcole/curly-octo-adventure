package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.Random;

public class HallwaySnail extends BaseSnail{

    public int distance;

    public HallwaySnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int distance) {
        super(map, coordinate, direction, random);
        this.distance = distance;
    }

    @Override
    protected SnailResult doStep() {
        // TODO: Implement hallway behavior
        complete = true;
        return SnailResult.COMPLETE;
    }

    @Override
    public BaseSnail createCopy() {
        return new HallwaySnail(map, coordinate.cpy(), direction, random, distance);
    }
}
