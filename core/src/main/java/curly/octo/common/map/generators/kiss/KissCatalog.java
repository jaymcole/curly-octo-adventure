package curly.octo.common.map.generators.kiss;

import java.util.ArrayList;
import java.util.HashMap;

public class KissCatalog {

    private final HashMap<String, ArrayList<KissEntrance>> entranceKeyToEntranceMap;
    private final HashMap<String, ArrayList<KissTemplate>> tagToTemplateMap;

    public KissCatalog() {
        entranceKeyToEntranceMap = new HashMap<>();
        tagToTemplateMap = new HashMap<>();
    }

    public void addTemplate(KissTemplate template) {
        for(KissEntrance entrance : template.templatesEntrances) {
            if (!entranceKeyToEntranceMap.containsKey(entrance.getKey())) {
                entranceKeyToEntranceMap.put(entrance.getKey(), new ArrayList<>());
            }
            entranceKeyToEntranceMap.get(entrance.getKey()).add(entrance);
        }

        for(String tag : template.jsonConfigs.tags) {
            if (!tagToTemplateMap.containsKey(tag)) {
                tagToTemplateMap.put(tag, new ArrayList<>());
            }
            tagToTemplateMap.get(tag).add(template);
        }
    }

    public ArrayList<KissEntrance> getCompatibleEntrances(KissEntrance entrance) {
        return entranceKeyToEntranceMap.getOrDefault(entrance.getMatchingKey(), new ArrayList<>());
    }

    public ArrayList<KissTemplate> getTemplateByTag(String tag) {
        if (tagToTemplateMap.containsKey(tag)) {
            return tagToTemplateMap.get(tag);
        }
        return new ArrayList<>();
    }

}
