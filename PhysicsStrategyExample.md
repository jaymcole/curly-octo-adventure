# Physics Body Builder Strategy Examples

The physics body building system has been refactored to support different strategies. Here's how to use them:

## Default Usage (All Tiles Strategy)
```java
GameMap gameMap = new GameMap(50, 10, 50, System.currentTimeMillis());
// Uses PhysicsStrategy.ALL_TILES by default
```

## Using BFS Boundary Strategy
```java
GameMap gameMap = new GameMap(50, 10, 50, System.currentTimeMillis());
gameMap.setPhysicsStrategy(GameMap.PhysicsStrategy.BFS_BOUNDARY);
gameMap.regeneratePhysics();
```

## Comparing Strategies
```java
GameMap gameMap = new GameMap(50, 10, 50, System.currentTimeMillis());

// Test with All Tiles strategy
gameMap.setPhysicsStrategy(GameMap.PhysicsStrategy.ALL_TILES);
gameMap.regeneratePhysics();
gameMap.logPerformanceMetrics();

// Test with BFS Boundary strategy  
gameMap.setPhysicsStrategy(GameMap.PhysicsStrategy.BFS_BOUNDARY);
gameMap.regeneratePhysics();
gameMap.logPerformanceMetrics();
```

## Expected Performance Differences

### All Tiles Strategy
- **Pros**: Simple, predictable, reliable collision everywhere
- **Cons**: Higher triangle count for maps with solid interiors
- **Best for**: Small maps or maps that are mostly hollow

### BFS Boundary Strategy  
- **Pros**: Potentially much fewer physics triangles for complex maps
- **Cons**: More complex implementation, only builds collision for reachable areas
- **Best for**: Large maps with significant solid interiors that players can't reach

## Architecture

- `PhysicsBodyBuilder` - Abstract base class
- `AllTilesPhysicsBodyBuilder` - Builds physics for all occupied tiles (original approach)
- `BFSPhysicsBodyBuilder` - Builds physics only for boundary tiles adjacent to reachable space
- `GameMap.PhysicsStrategy` - Enum to select which strategy to use

The builders are created and used automatically by `GameMap.generateTriangleMeshPhysics()` based on the selected strategy.