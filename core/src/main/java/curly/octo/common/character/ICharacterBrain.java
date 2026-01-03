package curly.octo.common.character;

/**
 * Represents the "brain" of a character, controlling its actions.
 * This can be an AI, a player input handler, or a remote network controller.
 */
public interface ICharacterBrain {

    /**
     * Sets the character this brain will control.
     * @param character The character instance.
     */
    void setCharacter(GameCharacter character);

    /**
     * Updates the character's state based on the brain's logic.
     * This is called every frame by the character that owns this brain.
     *
     * @param delta Time since last update.
     */
    void update(float delta);
}
