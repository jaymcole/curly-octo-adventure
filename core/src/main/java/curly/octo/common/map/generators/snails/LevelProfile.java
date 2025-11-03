package curly.octo.common.map.generators.snails;

/**
 * Defines different level profiles with pre-configured snail type distributions.
 * Makes it easy to have different generation styles for different areas or difficulty levels.
 */
public enum LevelProfile {

    /**
     * Balanced mix suitable for most dungeon areas.
     */
    BALANCED("balanced") {
        @Override
        public SnailTypeRegistry createRegistry() {
            return new SnailTypeRegistry()
                .addSnailType(SnailType.HALLWAY, 0.4f)
                .addSnailType(SnailType.ROOM, 0.6f);
        }
    };


    private final String name;

    LevelProfile(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Create a snail type registry configured for this level profile.
     */
    public abstract SnailTypeRegistry createRegistry();
}
