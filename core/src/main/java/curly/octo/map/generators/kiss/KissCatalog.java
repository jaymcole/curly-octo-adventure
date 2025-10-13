package curly.octo.map.generators.kiss;

import java.util.ArrayList;
import java.util.HashMap;

public class KissCatalog {

    private final HashMap<String, ArrayList<KissEntrance>> entranceKeyToEntranceMap;

    public KissCatalog() {
        entranceKeyToEntranceMap = new HashMap<>();
    }

    public void addTemplate(KissTemplate template) {
        for(KissEntrance entrance : template.templatesEntrances) {
            if (!entranceKeyToEntranceMap.containsKey(entrance.getKey())) {
                entranceKeyToEntranceMap.put(entrance.getKey(), new ArrayList<>());
            }
            entranceKeyToEntranceMap.get(entrance.getKey()).add(entrance);
        }
    }

    public ArrayList<KissEntrance> getCompatibleEntrances(KissEntrance entrance) {
        return entranceKeyToEntranceMap.getOrDefault(entrance.getMatchingKey(), new ArrayList<>());
    }

}
