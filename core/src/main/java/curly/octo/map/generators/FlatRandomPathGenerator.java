package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.MapTile;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileGeometryType;
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
                if (inBounds((int) wallSpot.x, (int) wallSpot.y, (int) wallSpot.z)) {
                    map[(int) wallSpot.x][(int) wallSpot.y + i][(int) wallSpot.z].geometryType = MapTileGeometryType.FULL;
                } else {
                    break;
                }
            }
        }

        for(Vector3 ceilingSpot : ceilingNeeded) {
            map[(int) ceilingSpot.x][Math.min((int) ceilingSpot.y + wallheight, height-1)][(int) ceilingSpot.z].geometryType = MapTileGeometryType.FULL;

        }
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
}
