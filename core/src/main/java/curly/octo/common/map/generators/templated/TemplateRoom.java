package curly.octo.common.map.generators.templated;

import curly.octo.common.map.enums.Direction;

import java.util.HashSet;

public class TemplateRoom {

    public final String collection_name;
    public final String template_name;
    public final int[][][] walls;
    public final HashSet<Direction> entrances;
    public final HashSet<Direction> exits;
    public final TemplateRoomConfigs configs;

    public TemplateRoom(String collection_name, String template_name, TemplateRoomConfigs configs, int[][][] walls) {
        this.configs = configs;
        this.collection_name = collection_name;
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
                return false;
            }
        }
        return true;
    }
}
