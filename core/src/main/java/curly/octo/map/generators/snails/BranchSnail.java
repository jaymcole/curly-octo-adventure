package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A snail that spawns multiple branch snails at the current location.
 * Each branch starts with the same position but can have different behaviors.
 */
public class BranchSnail extends BaseSnail {

    private final BaseSnail[] branches;
    private boolean branched = false;

    public BranchSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, BaseSnail... branches) {
        super(map, coordinate, direction, random);
        this.branches = branches.clone();
    }

    @Override
    protected SnailResult doStep() {
        if (branched) {
            complete = true;
            return SnailResult.COMPLETE;
        }
        
        // Create copies of all branch snails at current position
        List<BaseSnail> spawned = new ArrayList<>();
        
        for (BaseSnail branch : branches) {
            // Create a copy of the branch snail at our current position
            BaseSnail branchCopy = branch.createCopy();
            // Update position and direction to match current snail
            branchCopy.coordinate = this.coordinate.cpy();
            branchCopy.direction = this.direction;
            spawned.add(branchCopy);
        }
        
        branched = true;
        complete = true;
        
        return SnailResult.spawn(spawned.toArray(new BaseSnail[0]));
    }

    @Override
    public BaseSnail createCopy() {
        // Create copies of all branches
        BaseSnail[] branchCopies = new BaseSnail[branches.length];
        for (int i = 0; i < branches.length; i++) {
            branchCopies[i] = branches[i].createCopy();
        }
        
        BranchSnail copy = new BranchSnail(map, coordinate.cpy(), direction, random, branchCopies);
        copy.branched = this.branched;
        return copy;
    }
}