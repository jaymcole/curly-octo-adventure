package curly.octo.common.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.enums.Direction;

import java.util.*;

/**
 * Registry for managing snail types and their weighted selection.
 * Supports different level profiles with varying snail type distributions.
 */
public class SnailTypeRegistry {

    private final Map<SnailType, Float> weights;
    private final List<WeightedSnailType> weightedTypes;
    private float totalWeight;

    public SnailTypeRegistry() {
        this.weights = new EnumMap<>(SnailType.class);
        this.weightedTypes = new ArrayList<>();
        this.totalWeight = 0f;
    }

    /**
     * Add a snail type with its selection weight.
     * Higher weights make the snail type more likely to be selected.
     */
    public SnailTypeRegistry addSnailType(SnailType type, float weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }

        weights.put(type, weight);
        rebuildWeightedList();
        return this;
    }

    /**
     * Remove a snail type from the registry.
     */
    public SnailTypeRegistry removeSnailType(SnailType type) {
        weights.remove(type);
        rebuildWeightedList();
        return this;
    }

    /**
     * Update the weight of an existing snail type.
     */
    public SnailTypeRegistry updateWeight(SnailType type, float weight) {
        if (weights.containsKey(type)) {
            weights.put(type, weight);
            rebuildWeightedList();
        }
        return this;
    }

    /**
     * Select a random snail type based on the configured weights.
     */
    public SnailType selectRandomType(Random random) {
        if (weightedTypes.isEmpty()) {
            throw new IllegalStateException("No snail types registered");
        }

        float randomValue = random.nextFloat() * totalWeight;
        float currentWeight = 0f;

        for (WeightedSnailType weightedType : weightedTypes) {
            currentWeight += weightedType.weight;
            if (randomValue <= currentWeight) {
                return weightedType.type;
            }
        }

        // Fallback to last type (shouldn't happen with proper weights)
        return weightedTypes.get(weightedTypes.size() - 1).type;
    }

    /**
     * Create a snail instance of the selected type.
     */
    public BaseSnail createRandomSnail(GameMap map, Vector3 pos, Direction dir, Random random) {
        SnailType selectedType = selectRandomType(random);
        return selectedType.create(map, pos, dir, random);
    }

    /**
     * Get all registered snail types and their weights.
     */
    public Map<SnailType, Float> getWeights() {
        return new EnumMap<>(weights);
    }

    private void rebuildWeightedList() {
        weightedTypes.clear();
        totalWeight = 0f;

        for (Map.Entry<SnailType, Float> entry : weights.entrySet()) {
            weightedTypes.add(new WeightedSnailType(entry.getKey(), entry.getValue()));
            totalWeight += entry.getValue();
        }

        // Sort by weight for more predictable behavior
        weightedTypes.sort((a, b) -> Float.compare(a.weight, b.weight));
    }

    private static class WeightedSnailType {
        final SnailType type;
        final float weight;

        WeightedSnailType(SnailType type, float weight) {
            this.type = type;
            this.weight = weight;
        }
    }
}
