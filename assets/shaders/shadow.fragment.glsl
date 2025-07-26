#ifdef GL_ES
precision mediump float;
#endif

uniform vec3 u_lightPosition;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_diffuseColor;
uniform vec3 u_ambientLight;
uniform sampler2D u_shadowMap;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec4 v_lightSpacePos;

float calculateShadow() {
    // Distance-based shadow falloff - only apply shadows within reasonable range
    float distanceToLight = length(u_lightPosition - v_worldPos);
    if (distanceToLight > 15.0) {
        return 0.0; // No shadows beyond this distance
    }
    
    // Perspective divide
    vec3 projCoords = v_lightSpacePos.xyz / v_lightSpacePos.w;
    
    // Transform to [0,1] range
    projCoords = projCoords * 0.5 + 0.5;
    
    // Only apply shadows within the shadow map bounds
    if (projCoords.x < 0.0 || projCoords.x > 1.0 || 
        projCoords.y < 0.0 || projCoords.y > 1.0 || 
        projCoords.z > 1.0 || projCoords.z < 0.0) {
        return 0.0; // No shadow outside the shadow map
    }
    
    // Get closest depth value from light's perspective
    float closestDepth = texture2D(u_shadowMap, projCoords.xy).r;
    
    // If depth is at maximum (1.0), there's no geometry - no shadow
    if (closestDepth >= 0.999) {
        return 0.0;
    }
    
    // Current fragment depth from light's perspective
    float currentDepth = projCoords.z;
    
    // More conservative bias to reduce light leaking
    float bias = 0.002;
    
    // Shadow test with distance-based softening
    float shadow = currentDepth - bias > closestDepth ? 1.0 : 0.0;
    
    // Fade shadows at distance
    float shadowFalloff = 1.0 - smoothstep(10.0, 15.0, distanceToLight);
    shadow *= shadowFalloff;
    
    return shadow;
}

void main() {
    vec3 normal = normalize(v_normal);
    vec3 lightDirection = normalize(u_lightPosition - v_worldPos);
    
    // Calculate distance attenuation
    float distance = length(u_lightPosition - v_worldPos);
    float attenuation = u_lightIntensity / (1.0 + 0.09 * distance + 0.032 * distance * distance);
    
    // Diffuse lighting calculation
    float diff = max(dot(normal, lightDirection), 0.0);
    vec3 diffuse = diff * u_lightColor * attenuation;
    
    // Shadow calculation
    float shadow = calculateShadow();
    
    // Final color with shadows
    vec3 lighting = u_ambientLight + (1.0 - shadow) * diffuse;
    vec3 finalColor = u_diffuseColor * lighting;
    
    // Debug: Show shadow map bounds
    // Uncomment the lines below to visualize shadow map coverage
    // vec3 projCoords = v_lightSpacePos.xyz / v_lightSpacePos.w;
    // projCoords = projCoords * 0.5 + 0.5;
    // if (projCoords.x < 0.0 || projCoords.x > 1.0 || projCoords.y < 0.0 || projCoords.y > 1.0) {
    //     finalColor = vec3(1.0, 0.0, 0.0); // Red for areas outside shadow map
    // }
    
    gl_FragColor = vec4(finalColor, 1.0);
}