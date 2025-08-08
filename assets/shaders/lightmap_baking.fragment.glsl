#ifdef GL_ES
precision mediump float;
#endif

uniform vec3 u_lightPosition;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_ambientLight;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;

void main() {
    vec3 normal = normalize(v_normal);
    vec3 lightDir = normalize(u_lightPosition - v_worldPos);
    float distance = length(u_lightPosition - v_worldPos);
    
    // More forgiving attenuation for lightmap baking (less aggressive falloff)
    float attenuation = u_lightIntensity / (1.0 + 0.01 * distance + 0.001 * distance * distance);
    
    // Diffuse lighting
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * u_lightColor * attenuation;
    
    // Combine with ambient
    vec3 finalColor = u_ambientLight + diffuse;
    gl_FragColor = vec4(finalColor, 1.0);
}