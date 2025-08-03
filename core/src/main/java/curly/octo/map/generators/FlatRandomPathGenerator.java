package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.MapTile;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.SpawnPointHint;

import java.util.ArrayList;
import java.util.Random;

public class FlatRandomPathGenerator extends MapGenerator {
    private class BranchSpawn {
        public int x, y, z;
        public CardinalDirection direction;
        public int hallwaySize = 3;

        public BranchSpawn(int x, int y, int z, CardinalDirection direction) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.direction = direction;
        }

        public Vector3 getVector3Position() {
            return new Vector3(x,y,z);
        }
    }

    public static final float BRANCH_FREQUENCY = 0.85f;
    public static final float PATH_CONTINUATION_FREQUENCY = 0.1f;

    private ArrayList<BranchSpawn> branches;

    public FlatRandomPathGenerator(Random random, int width, int height, int depth) {
        super(random, width, height, depth);
        branches = new ArrayList<>();
    }

    @Override
    public MapTile[][][] generate() {
        Vector3 center = getMapCenter();
        map[(int) center.x][(int) (center.y + 1)][(int) center.z].AddHint(new SpawnPointHint());

        int roomSize = 4;
        createRoom(center, roomSize, 3, roomSize);

        branches.add(new BranchSpawn((int) center.x, (int) center.y, (int) center.z, CardinalDirection.NORTH));
        branches.add(new BranchSpawn((int) center.x, (int) center.y, (int) center.z, CardinalDirection.EAST));
        branches.add(new BranchSpawn((int) center.x, (int) center.y, (int) center.z, CardinalDirection.SOUTH));
        branches.add(new BranchSpawn((int) center.x, (int) center.y, (int) center.z, CardinalDirection.WEST));
        for(int b = 0; b < branches.size(); b++) {
            for(int i = 0; i < (roomSize/2)-1; i++) {
                advanceBranch(branches.get(b));
            }
        }

        while(!branches.isEmpty()) {
            exploreBranch(branches.remove(0));
        }

        buildWalls();
        return map;
    }

    @Override
    protected void createRoom(Vector3 center, int roomWidth, int roomHeight, int roomDepth) {
        for(int x = (int)(center.x - (roomWidth/2)); x < (int)(center.x + (roomWidth/2)); x++) {
            for(int z = (int)(center.z - (roomDepth/2)); z < (int)(center.z + (roomDepth/2)); z++) {
                if (inBounds(x, (int)center.y, z)) {

                    map[x][(int)center.y][z].geometryType = MapTileGeometryType.FULL;
                    map[x][(int)center.y + 1][z].fillType = MapTileFillType.LAVA;
                    // Assign varied floor materials
                    float materialRoll = random.nextFloat();
                    if (materialRoll < 0.5f) {
                        map[x][(int)center.y][z].material = MapTileMaterial.STONE;
                    } else if (materialRoll < 0.8f) {
                        map[x][(int)center.y][z].material = MapTileMaterial.DIRT;
                    } else {
                        map[x][(int)center.y][z].material = MapTileMaterial.GRASS;
                    }
                }
            }
        }
    }

    private void exploreBranch(BranchSpawn branch) {
        while (true) {
            advanceBranch(branch);
            if (inBounds(branch.x, branch.y, branch.z)) {
                if(map[branch.x][branch.y][branch.z].geometryType != MapTileGeometryType.EMPTY) {

                    return;
                }

                createRoom(branch.getVector3Position(), branch.hallwaySize, 1, branch.hallwaySize);

                Log.info("exploreBranch", "Placing a tile at: (" + branch.x + ", " + branch.y + ", " + branch.z + ")");
                if (random.nextFloat() > PATH_CONTINUATION_FREQUENCY) { // continue path, very likely
                    if (random.nextFloat() > BRANCH_FREQUENCY) {
                        // add new branch, unlikely
                        BranchSpawn newBranch = new BranchSpawn(branch.x, branch.y, branch.z, branch.direction);
                        for(int i = 0; i < (branch.hallwaySize / 2)-1; i++) {
                            advanceBranch(newBranch);
                        }
                        if (random.nextBoolean()) {
                            newBranch.direction = CardinalDirection.rotateClockwise(newBranch.direction);
                        } else {
                            newBranch.direction = CardinalDirection.rotateCounterClockwise(newBranch.direction);
                        }
                        branches.add(newBranch);
                    }
                }
            } else {
                return;
            }
        }
    }

    private void advanceBranch(BranchSpawn branch) {
        switch(branch.direction) {
            case NORTH:
                branch.x++;
                break;
            case EAST:
                branch.z++;
                break;
            case WEST:
                branch.z--;
                break;
            case SOUTH:
                branch.x--;
                break;
        }
    }

    private void buildWalls() {
        int wallheight = 5;

        ArrayList<Vector3> wallNeeded = new ArrayList<>();
        ArrayList<Vector3> ceilingNeeded = new ArrayList<>();


        for(int x = 0; x < width; x++) {
            for(int y = 0; y < height; y++) {
                for(int z = 0; z < depth; z++) {
                    if (map[x][y][z].geometryType == MapTileGeometryType.EMPTY) {
                        if (isNextToFloor(x, y, z)) {
                            wallNeeded.add(new Vector3(x,y,z));
                        }
                    } else {
                        ceilingNeeded.add(new Vector3(x,y,z));
                    }
                }
            }
        }

        for(Vector3 wallSpot : wallNeeded) {
            for(int i = 0; i < wallheight; i++) {
                if (inBounds((int) wallSpot.x, (int) wallSpot.y + i, (int) wallSpot.z)) {
                    map[(int) wallSpot.x][(int) wallSpot.y + i][(int) wallSpot.z].geometryType = MapTileGeometryType.FULL;
                    // Walls are stone material
                    map[(int) wallSpot.x][(int) wallSpot.y + i][(int) wallSpot.z].material = MapTileMaterial.WALL;
                } else {
                    break;
                }
            }
        }

        for(Vector3 ceilingSpot : ceilingNeeded) {
            int ceilingY = Math.min((int) ceilingSpot.y + wallheight, height-1);
            map[(int) ceilingSpot.x][ceilingY][(int) ceilingSpot.z].geometryType = MapTileGeometryType.FULL;
            // Ceilings are stone material
            map[(int) ceilingSpot.x][ceilingY][(int) ceilingSpot.z].material = MapTileMaterial.STONE;
        }

        addLights(ceilingNeeded);

    }

    private boolean isNextToFloor(int x, int y, int z) {
        for(int startX = x-1; startX < width; startX++) {
            for(int startZ = z-1; startZ < depth; startZ++) {
                if (inBounds(startX, y, startZ)) {
                    if (map[startX][y][startZ].geometryType != MapTileGeometryType.EMPTY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void addLights(ArrayList<Vector3> ceilingNeeded) {
        int numberOfLightsToAdd = 5; // Add more lights for testing
        for(int i = 0; i < numberOfLightsToAdd; i++) {

            Vector3 nextLightSpot = ceilingNeeded.get(random.nextInt(ceilingNeeded.size())-1);
            MapTile tile = map[(int) nextLightSpot.x][(int) nextLightSpot.y + 2][(int) nextLightSpot.z];
            LightHint light = new LightHint();
            light.intensity = 2; // Increase intensity for visibility

            // Set different colors for variety
            switch(random.nextInt(3)) {
                case 0: // Warm torch light
                    light.color_r = 0.3f;
                    light.color_g = 1.0f;
                    light.color_b = 0.4f;
                    break;
                case 1: // Cool blue crystal
                    light.color_r = 0.4f;
                    light.color_g = 0.6f;
                    light.color_b = 1.0f;
                    break;
                case 2: // Green mystical light
                    light.color_r = 0.2f;
                    light.color_g = 1.0f;
                    light.color_b = 0.3f;
                    break;
            }

            tile.AddHint(light);
        }
    }
}
