package curly.octo.map.hints;

import java.util.UUID;

public class LightHint extends MapHint{
    public String entityId = UUID.randomUUID().toString();
    public float intensity;
    public float color_r;
    public float color_g;
    public float color_b;
    public float[] flicker;

    // Default constructor for Kryo
    public LightHint() {
        super();
    }

    public LightHint(Long tileLookupKey) {
        super(tileLookupKey);
    }
}
