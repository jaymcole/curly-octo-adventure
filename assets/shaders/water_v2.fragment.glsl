#ifdef GL_ES
precision mediump float;
#endif

// ============================================================================
// IMPROVED WATER SHADER V2 - With Perlin Noise Wave Animation
// Based on Shadertoy Perlin noise implementation
// Adapted for LibGDX voxel-based water rendering
// ============================================================================

// Material and environment
uniform vec3 u_ambientLight;
uniform vec3 u_lightPositions[8];
uniform vec3 u_lightColors[8];
uniform float u_lightIntensities[8];
uniform int u_numLights;
uniform float u_time;  // Time in seconds for animation

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;
varying float v_waveHeight;  // Wave displacement from vertex shader

// ============================================================================
// PERLIN NOISE FUNCTIONS - Extracted from Shadertoy
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

// 3D Perlin noise - Based on https://mrl.nyu.edu/~perlin/noise/
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

// Fractional Brownian Motion - Layered noise for detail
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

// Get animated noise for water effects
float getWaveNoise(vec3 p, float time)
{
    return fbm(p + vec3(0.3 * time, 0.0, 0.1 * time), 4, 0.5);
}

// ============================================================================
// WATER RENDERING
// ============================================================================

vec3 calculateWaterNormal(vec3 worldPos, vec3 baseNormal, float time)
{
    // Create normal perturbation using Perlin noise
    float noiseScale = 2.0;  // Scale of noise pattern
    float noiseStrength = 0.3;  // Strength of normal perturbation

    // Sample noise at different positions for normal calculation
    float noiseCenter = getWaveNoise(worldPos * noiseScale, time);
    float noiseX = getWaveNoise((worldPos + vec3(0.1, 0.0, 0.0)) * noiseScale, time);
    float noiseZ = getWaveNoise((worldPos + vec3(0.0, 0.0, 0.1)) * noiseScale, time);

    // Calculate gradient for normal perturbation
    vec3 perturbation = vec3(
        (noiseCenter - noiseX) * noiseStrength,
        1.0,
        (noiseCenter - noiseZ) * noiseStrength
    );

    // Blend perturbed normal with base normal
    vec3 perturbedNormal = normalize(baseNormal + perturbation);
    return perturbedNormal;
}

vec3 calculateWaterColor(vec3 worldPos, float time)
{
    // Base water color
    vec3 deepWaterColor = vec3(0.1, 0.3, 0.6);  // Deep blue
    vec3 shallowWaterColor = vec3(0.3, 0.6, 0.9);  // Light blue

    // Use noise to create depth variation
    float depthNoise = getWaveNoise(worldPos * 0.5, time * 0.3) * 0.5 + 0.5;

    // Mix deep and shallow colors based on noise
    vec3 waterColor = mix(deepWaterColor, shallowWaterColor, depthNoise);

    // Add foam/sparkle effect at wave peaks (based on wave height from vertex shader)
    float foamAmount = smoothstep(0.02, 0.08, abs(v_waveHeight));
    vec3 foamColor = vec3(0.9, 0.95, 1.0);
    waterColor = mix(waterColor, foamColor, foamAmount * 0.4);

    return waterColor;
}

float calculateWaterAlpha(vec3 worldPos, float time)
{
    // Base transparency
    float baseAlpha = 0.5;

    // Vary alpha with noise for more realistic water
    float alphaNoise = getWaveNoise(worldPos * 1.5, time * 0.5) * 0.5 + 0.5;

    // More opaque at wave peaks (foam effect)
    float foamAlpha = smoothstep(0.02, 0.08, abs(v_waveHeight)) * 0.3;

    return baseAlpha + alphaNoise * 0.2 + foamAlpha;
}

void main() {
    // Calculate animated water normal
    vec3 normal = calculateWaterNormal(v_worldPos, normalize(v_normal), u_time);

    // Calculate water color with noise-based variation
    vec3 waterColor = calculateWaterColor(v_worldPos, u_time);

    // Start with ambient lighting
    vec3 totalLighting = u_ambientLight;

    // Calculate lighting from all dynamic lights
    for (int i = 0; i < 8; i++) {
        if (i >= u_numLights) break;

        vec3 lightPos = u_lightPositions[i];
        vec3 lightColor = u_lightColors[i];
        float lightIntensity = u_lightIntensities[i];

        vec3 lightDirection = v_worldPos - lightPos;
        vec3 lightDir = normalize(-lightDirection);
        float distance = length(lightDirection);
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);

        // Diffuse lighting with perturbed normal
        float diff = max(dot(normal, lightDir), 0.0);

        // Add subtle specular highlight for water surface
        vec3 viewDir = normalize(-v_worldPos);  // Simplified view direction
        vec3 reflectDir = reflect(-lightDir, normal);
        float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);

        vec3 lightContribution = (diff + spec * 0.5) * lightColor * attenuation;
        totalLighting += lightContribution;
    }

    // Apply lighting to water color
    vec3 finalColor = waterColor * totalLighting;

    // Calculate final alpha with variation
    float alpha = calculateWaterAlpha(v_worldPos, u_time);

    gl_FragColor = vec4(finalColor, alpha);
}
