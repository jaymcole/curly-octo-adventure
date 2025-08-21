package curly.octo.map.hints;

public abstract class MapHint {
    public Long tileLookupKey;

    // Default constructor for Kryo
    public MapHint() {
    }

    public MapHint(Long tileLookupKey) {
        this.tileLookupKey = tileLookupKey;
    }

}
