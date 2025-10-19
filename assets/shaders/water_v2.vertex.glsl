// ============================================================================
// IMPROVED WATER VERTEX SHADER V2 - With Perlin Noise Wave Displacement
// Adapted from Shadertoy Perlin noise for LibGDX voxel water
// ============================================================================

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;
uniform float u_time;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;
varying float v_waveHeight;  // Pass wave displacement to fragment shader

// ============================================================================
// PERLIN NOISE FUNCTIONS FOR VERTEX DISPLACEMENT
// ============================================================================

#define HASHSCALE 0.1031

float hash(vec3 p3)
{
    p3 = fract(p3 * HASHSCALE);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 fade(vec3 t) {
    return t * t * t * (t * (6.0 * t - 15.0) + 10.0);
}

float grad(float hash, vec3 p)
{
    int h = int(1e4 * hash) & 15;
    float u = h < 8 ? p.x : p.y;
    float v = h < 4 ? p.y : (h == 12 || h == 14 ? p.x : p.z);
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
}

// 3D Perlin noise
float perlinNoise3D(vec3 p)
{
    vec3 pi = floor(p);
    vec3 pf = p - pi;
    vec3 w = fade(pf);

    return mix(
        mix(
            mix(grad(hash(pi + vec3(0, 0, 0)), pf - vec3(0, 0, 0)),
                grad(hash(pi + vec3(1, 0, 0)), pf - vec3(1, 0, 0)), w.x),
            mix(grad(hash(pi + vec3(0, 1, 0)), pf - vec3(0, 1, 0)),
                grad(hash(pi + vec3(1, 1, 0)), pf - vec3(1, 1, 0)), w.x), w.y),
        mix(
            mix(grad(hash(pi + vec3(0, 0, 1)), pf - vec3(0, 0, 1)),
                grad(hash(pi + vec3(1, 0, 1)), pf - vec3(1, 0, 1)), w.x),
            mix(grad(hash(pi + vec3(0, 1, 1)), pf - vec3(0, 1, 1)),
                grad(hash(pi + vec3(1, 1, 1)), pf - vec3(1, 1, 1)), w.x), w.y), w.z);
}

// Fractional Brownian Motion - layered noise
float fbm(vec3 pos, int octaves, float persistence)
{
    float total = 0.0;
    float frequency = 1.0;
    float amplitude = 1.0;
    float maxValue = 0.0;

    for(int i = 0; i < octaves; ++i)
    {
        total += perlinNoise3D(pos * frequency) * amplitude;
        maxValue += amplitude;
        amplitude *= persistence;
        frequency *= 2.0;
    }

    return total / maxValue;
}

// Get wave displacement using animated noise
float getWaveDisplacement(vec3 p, float time)
{
    // Slower, more organic wave movement
    vec3 animatedPos = p + vec3(0.3 * time, 0.0, 0.1 * time);
    return fbm(animatedPos, 3, 0.5);  // 3 octaves for performance
}

void main() {
    // Pass through texture coordinates
    v_texCoord = a_texCoord0;

    // Transform vertex to world space
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);

    // Calculate wave displacement using Perlin noise
    float waveScale = 1.5;  // Scale of wave pattern
    float waveAmplitude = 0.15;  // Height of waves
    float waveSpeed = 0.5;  // Speed of wave animation

    float displacement = getWaveDisplacement(worldPos.xyz * waveScale, u_time * waveSpeed);

    // Apply vertical displacement to create waves
    worldPos.y += displacement * waveAmplitude;

    // Store wave height for foam effects in fragment shader
    v_waveHeight = displacement * waveAmplitude;

    // Store world position for fragment shader
    v_worldPos = worldPos.xyz;

    // Transform normal to world space
    // Note: Normal will be further perturbed in fragment shader for lighting
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);

    // Transform to camera projection space
    gl_Position = u_projViewTrans * worldPos;
}

