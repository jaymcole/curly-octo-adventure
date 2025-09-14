package curly.octo;

public class Constants {

    // =========================
    // NETWORK CONFIGURATION
    // =========================

    public static final int MAP_TRANSFER_CHUNK_DELAY = 10;

    /** Default TCP port for server connections */
    public static final int NETWORK_TCP_PORT = 54555;

    /** Default UDP port for server connections */
    public static final int NETWORK_UDP_PORT = 54777;

    /** Chunk size for map transfer (8KB) */
    public static final int NETWORK_CHUNK_SIZE = 8192;

    /** Network buffer size for read/write operations (32KB) */
    public static final int NETWORK_BUFFER_SIZE = 32768;

    /** Position update interval in nanoseconds (50 FPS = 20ms) */
    public static final long NETWORK_POSITION_UPDATE_INTERVAL_NS = 20_000_000L;


    // =========================
    // MAP GENERATION
    // =========================

    /** Size of each map tile in world units */
    public static final float MAP_TILE_SIZE = 3f;

    /** Number of tiles per chunk side (16x16 chunks) */
    public static final int MAP_CHUNK_SIZE = 16;

    /** Default seed for procedural map generation */
    public static final long MAP_GENERATION_SEED = 1756347946230L;


    // =========================
    // PHYSICS SYSTEM
    // =========================

    /** Gravity acceleration (negative Y direction) */
    public static final float PHYSICS_GRAVITY = -50f;

    /** Maximum walkable slope in degrees */
    public static final float PHYSICS_MAX_SLOPE_DEGREES = 60f;

    /** Fixed time step for physics simulation (120 FPS) */
    public static final float PHYSICS_FIXED_TIME_STEP = 1f / 120f;

    /** Maximum physics substeps per frame */
    public static final int PHYSICS_MAX_SUBSTEPS = 10;


    // =========================
    // PLAYER CONFIGURATION
    // =========================

    public static final boolean RENDER_SELF = true;

    /** Player character height in world units */
    public static final float PLAYER_HEIGHT = 2.5f;

    /** Player movement speed multiplier */
    public static final float PLAYER_MOVEMENT_SPEED = 0.3f;

    /** Force applied when player jumps */
    public static final float PLAYER_JUMP_FORCE = 25f;

    /** Maximum upward camera pitch in degrees */
    public static final float PLAYER_CAMERA_MAX_PITCH = 89f;

    /** Maximum downward camera pitch in degrees */
    public static final float PLAYER_CAMERA_MIN_PITCH = -89f;

    /** Path to player 3D model asset */
    public static final String PLAYER_MODEL_PATH = "models/character/crate.gltf";

    /** Scale factor for player model rendering */
    public static final float PLAYER_MODEL_SCALE = 0.1f;

    /** Fly mode movement speed multiplier (for debugging) */
    public static final float PLAYER_FLY_SPEED = 50f;


    // =========================
    // CAMERA AND RENDERING
    // =========================

    /** Main camera field of view in degrees */
    public static final float CAMERA_FOV = 67f;

    /** Main camera near clipping plane */
    public static final float CAMERA_NEAR_PLANE = 0.1f;

    /** Main camera far clipping plane */
    public static final float CAMERA_FAR_PLANE = 3000f;

    /** Render fog start distance */
    public static final float RENDER_FOG_START = 150f;

    /** Render fog end distance */
    public static final float RENDER_FOG_END = 300f;

    /** Distance for chunk rendering around player */
    public static final float CHUNK_RENDER_DISTANCE = 3000f;


    // =========================
    // SHADOW MAPPING
    // =========================

    /** Resolution of shadow maps (1024x1024) */
    public static final int SHADOW_MAP_SIZE = 1024;

    /** Field of view for shadow-casting light cameras */
    public static final float SHADOW_LIGHT_CAMERA_FOV = 120f;

    /** Near plane for shadow light cameras */
    public static final float SHADOW_LIGHT_CAMERA_NEAR = 0.1f;

    /** Far plane for shadow light cameras */
    public static final float SHADOW_LIGHT_CAMERA_FAR = 40f;

    /** Field of view for cube shadow cameras (90° for cube faces) */
    public static final float CUBE_SHADOW_CAMERA_FOV = 90f;

    /** Maximum range for shadow camera rendering */
    public static final float SHADOW_CAMERA_FAR_RANGE = 60f;


    // =========================
    // LIGHTING SYSTEM
    // =========================

    /** Maximum number of shadow-casting lights supported */
    public static final int LIGHTING_MAX_SHADOW_LIGHTS = 8;

    /** Maximum cube shadow map textures (8 lights × 6 faces) */
    public static final int LIGHTING_MAX_CUBE_SHADOW_MAPS = 48;

    /** Maximum number of fallback lights (non-shadow) */
    public static final int LIGHTING_MAX_FALLBACK_LIGHTS = 256;

    /** Enhanced shader light limit (modern GPUs) */
    public static final int LIGHTING_ENHANCED_SHADER_LIGHTS = 32;

    /** Fallback shader light limit (older GPUs) */
    public static final int LIGHTING_FALLBACK_SHADER_LIGHTS = 8;

    /** Interval between lighting culling updates in milliseconds */
    public static final long LIGHTING_CULL_INTERVAL_MS = 100L;

    /** Interval for performance logging in seconds */
    public static final float LIGHTING_PERFORMANCE_LOG_INTERVAL = 5.0f;

    /** Distance threshold for light culling */
    public static final float LIGHTING_CULL_DISTANCE = 100f;

    /** Minimum light intensity for rendering optimization */
    public static final float LIGHTING_MIN_INTENSITY_THRESHOLD = 0.005f;


    // =========================
    // PERFORMANCE SETTINGS
    // =========================

    /** Target frames per second for game loop */
    public static final int GAME_TARGET_FPS = 60;

    /** Target frame time in nanoseconds */
    public static final long GAME_FRAME_TIME_NS = 1_000_000_000L / GAME_TARGET_FPS;

    /** Number of frames between performance reports (5 seconds at 60fps) */
    public static final int RENDERING_PERFORMANCE_REPORT_FRAMES = 300;

    /** Maximum allowed frame time before performance warnings */
    public static final long PERFORMANCE_WARNING_THRESHOLD_NS = 20_000_000L; // 20ms


    // =========================
    // GEOMETRIC CONSTANTS
    // =========================

    /** Number of faces on a cube */
    public static final int CUBE_FACE_COUNT = 6;

    /** X direction offsets for cube faces */
    public static final int[] CUBE_FACE_DX = {-1, 1, 0, 0, 0, 0};

    /** Y direction offsets for cube faces */
    public static final int[] CUBE_FACE_DY = {0, 0, -1, 1, 0, 0};

    /** Z direction offsets for cube faces */
    public static final int[] CUBE_FACE_DZ = {0, 0, 0, 0, -1, 1};


    // =========================
    // ASSET AND MODEL CONSTANTS
    // =========================

    /** Radius for spawn marker visualization */
    public static final float SPAWN_MARKER_RADIUS = 2f;

    /** Number of segments for spawn marker sphere */
    public static final int SPAWN_MARKER_SEGMENTS = 10;

    /** Approximate face count for spawn marker geometry */
    public static final int SPAWN_MARKER_FACE_COUNT = 200;


    // =========================
    // DEVELOPMENT AND DEBUG
    // =========================

    /** Enable debug physics wireframes */
    public static final boolean DEBUG_PHYSICS_ENABLED = false;

    /** Enable lighting performance monitoring */
    public static final boolean DEBUG_LIGHTING_PERFORMANCE = true;

    /** Enable verbose network logging */
    public static final boolean DEBUG_NETWORK_VERBOSE = false;

    /** Show FPS counter in development builds */
    public static final boolean DEBUG_SHOW_FPS = true;


    // =========================
    // DEMO AND TESTING
    // =========================

    /** Number of shadow lights in lighting demos */
    public static final int LIGHTING_DEMO_SHADOW_LIGHTS = 4;

    /** Total number of lights in lighting demos */
    public static final int LIGHTING_DEMO_TOTAL_LIGHTS = 20;

    /** Time modulo for animation cycles in milliseconds (60 seconds) */
    public static final long ANIMATION_TIME_MODULO_MS = 60000L;

}
