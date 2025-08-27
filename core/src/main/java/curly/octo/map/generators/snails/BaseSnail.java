package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.hints.LightHint;
import lights.LightPresets;

import java.util.Random;

public abstract class BaseSnail {

    public static Random randomLights;

    public final GameMap map;

    protected Vector3 coordinate;
    protected Direction direction;
    protected boolean complete;
    protected Random random;

    public BaseSnail(GameMap map, Vector3 coordinate, Direction direction, Random random) {
        if (randomLights == null) {
            randomLights = new Random();
        }
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

        if (random.nextFloat() > 0.1f) {
            addLight(coordinate);
        }

        return doStep();
    }

    private void addLight(Vector3 lightPos) {
        // Ensure light tile exists
        map.touchTile(lightPos);

        // Create light hint at position
        LightHint lightHint = new LightHint(map.constructKeyFromIndexCoordinates(
            (int)lightPos.x, (int)lightPos.y + 1, (int)lightPos.z));
        lightHint.color_r = randomLights.nextFloat();//0.8f;  // Warm white light
        lightHint.color_g = randomLights.nextFloat();//0.7f;
        lightHint.color_b = randomLights.nextFloat();//0.5f;
        lightHint.intensity = random.nextInt(5) + randomLights.nextFloat();//3f;  // Much lower intensity
        lightHint.intensity = randomLights.nextFloat();//3f;  // Much lower intensity
        lightHint.flicker = LightPresets.LIGHT_FLICKER_1;

        map.registerHint(lightHint);
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

    /**
     * Check if a tile already exists at the given coordinates.
     * @param coordinate The position to check
     * @return true if a tile already exists, false otherwise
     */
    protected boolean tileExists(Vector3 coordinate) {
        return map.getTile((int)coordinate.x, (int)coordinate.y, (int)coordinate.z) != null;
    }

    /**
     * Check if collision detection should be lenient due to small map size.
     * @return true if map is still small and should continue generating
     */
    protected boolean shouldBeLenientWithCollisions() {
        return map.getAllTiles().size() < 200; // MIN_MAP_SIZE
    }

    /**
     * Check if a tile already exists at the given coordinates.
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @return true if a tile already exists, false otherwise
     */
    protected boolean tileExists(int x, int y, int z) {
        return map.getTile(x, y, z) != null;
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
