package curly.octo.map.hints;

public class SpawnPointHint extends MapHint{
    // Default constructor for Kryo
    public SpawnPointHint() {
        super();
    }

    public SpawnPointHint(Long tileLookupKey) {
        super(tileLookupKey);
    }
}
