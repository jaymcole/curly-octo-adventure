package curly.octo.map.generators.templated;

import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.Direction;

import java.util.HashSet;
import java.util.stream.Collectors;

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
                    entrances.add(Direction.NORTH);
                    break;
                case 'e':
                    exits.add(Direction.EAST);
                    entrances.add(Direction.EAST);
                    break;
                case 's':
                    exits.add(Direction.SOUTH);
                    entrances.add(Direction.SOUTH);
                    break;
                case 'w':
                    exits.add(Direction.WEST);
                    entrances.add(Direction.WEST);
                    break;
            }
        }
    }

    public boolean isValidRoom(HashSet<Direction> enteringDirections) {
        if (template_name.startsWith("spawn")) {
            return false;
        }

        if (enteringDirections.isEmpty()) {
            return false;
        }

        for(Direction dir : enteringDirections) {
            if (!entrances.contains(dir)) {
                Log.info("isValidRoom", template_name + ": enteringDirections: [" + enteringDirections.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", ")) + "], no");

                return false;
            }
        }
        Log.info("isValidRoom", template_name + ": enteringDirections: [" + enteringDirections.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", ")) + "], yes");
        return true;
    }
}
