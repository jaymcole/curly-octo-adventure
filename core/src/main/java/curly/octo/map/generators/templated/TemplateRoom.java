package curly.octo.map.generators.templated;

import curly.octo.map.enums.Direction;

import java.util.HashSet;
import java.util.List;

public class TemplateRoom {

    public final String template_name;
    public final int[][][] walls;
    public final HashSet<Direction> entrances;
    public final HashSet<Direction> exits;

    public TemplateRoom(String template_name, int[][][] walls) {
        this.template_name = template_name;
        this.walls = walls;

        String[] nameParts = template_name.split("_");
        String exitDirections = nameParts[1];
        exits = new HashSet<>();
        entrances = new HashSet<>();
        for(char dir : exitDirections.toLowerCase().toCharArray()) {
            switch (dir) {
                case 'n':
                    exits.add(Direction.NORTH);
                    entrances.add(Direction.SOUTH);
                    break;
                case 'e':
                    exits.add(Direction.EAST);
                    entrances.add(Direction.WEST);
                    break;
                case 's':
                    exits.add(Direction.SOUTH);
                    entrances.add(Direction.NORTH);
                    break;
                case 'w':
                    exits.add(Direction.WEST);
                    entrances.add(Direction.EAST);
                    break;
            }
        }
    }

    public boolean isValidRoom(HashSet<Direction> enteringDirections) {
        for(Direction dir : enteringDirections) {
            if (!entrances.contains(dir)) {
                return false;
            }
        }
        return true;
    }
}
