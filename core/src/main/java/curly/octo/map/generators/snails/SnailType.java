package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.Turn;

import java.util.Random;

/**
 * Enum defining all available snail types with their factory methods.
 * Makes it easy to add new snail types without modifying the generation logic.
 */
public enum SnailType {

    HALLWAY("hallway", (map, pos, dir, random) ->
        new HallwaySnail(map, pos, dir, random, random.nextInt(15) + 1, 3, 3)),

    L_CORRIDOR("l_corridor", (map, pos, dir, random) -> {
        int length1 = random.nextInt(3, 8);
        int length2 = random.nextInt(3, 8);
        Turn turn = random.nextBoolean() ? Turn.CLOCKWISE : Turn.COUNTERCLOCKWISE;
        return new ForwardSnail(map, pos, dir, random, length1, 3)
                .then(new TurnSnail(map, pos, dir, random, turn))
                .then(new ForwardSnail(map, pos, dir, random, length2, 3));
    }),

    ROOM("room", (map, pos, dir, random) -> {
        int width = random.nextInt(4, 8);
        int depth = random.nextInt(4, 8);
        return new RoomSnail(map, pos, dir, random, width, 2, depth);
    });

    private final String name;
    private final SnailFactory factory;

    SnailType(String name, SnailFactory factory) {
        this.name = name;
        this.factory = factory;
    }

    public String getName() {
        return name;
    }

    public BaseSnail create(GameMap map, Vector3 pos, Direction dir, Random random) {
        return factory.create(map, pos, dir, random);
    }

    @FunctionalInterface
    public interface SnailFactory {
        BaseSnail create(GameMap map, Vector3 pos, Direction dir, Random random);
    }
}
