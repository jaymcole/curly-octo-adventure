/**
 * Hybrid Lighting Vertex Shader
 * 
 * This vertex shader provides the foundation for the hybrid lighting system,
 * supporting both shadow-casting and fallback lights. It transforms vertices
 * to world space and passes necessary data to the fragment shader for lighting
 * calculations.
 * 
 * Key Features:
 * - World space position calculation for lighting
 * - Normal transformation to world space
 * - Texture coordinate pass-through
 * - Optimized for performance with minimal calculations
 */

// Vertex attributes from the model
attribute vec3 a_position;    // Vertex position in model space
attribute vec3 a_normal;      // Vertex normal in model space  
attribute vec2 a_texCoord0;   // Texture coordinates

// Transformation matrices
uniform mat4 u_worldTrans;     // Model-to-world transformation matrix
uniform mat4 u_projViewTrans;  // Combined projection-view matrix

// Data passed to fragment shader
varying vec3 v_worldPos;       // World space position for lighting calculations
varying vec3 v_normal;         // World space normal for lighting calculations
varying vec2 v_texCoord;       // Texture coordinates for sampling

/**
 * Main vertex shader entry point.
 * Transforms vertex data from model space to clip space and prepares
 * interpolated values for the fragment shader.
 */
void main() {
    // Transform vertex position to world space
    // This is essential for lighting calculations as all lights are positioned in world space
    vec4 worldPosition = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPosition.xyz;
    
    // Transform normal to world space
    // We use the upper-left 3x3 part of the world transform for normal transformation
    // This assumes uniform scaling - for non-uniform scaling, use the inverse transpose
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    
    // Pass texture coordinates directly to fragment shader
    // No transformation needed as texture coordinates are already in [0,1] space
    v_texCoord = a_texCoord0;
    
    // Transform vertex to clip space for rendering pipeline
    // This is the final position used by the GPU for rasterization
    gl_Position = u_projViewTrans * worldPosition;
}