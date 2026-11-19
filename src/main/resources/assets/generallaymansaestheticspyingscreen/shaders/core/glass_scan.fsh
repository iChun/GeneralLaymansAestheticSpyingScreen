#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec2 faceCoord;
in vec3 localPos;
in float radius;
in float fade;
in float flash;

out vec4 fragColor;

const vec3 CYAN = vec3(0.30, 0.86, 1.0);
// Grid cells per block, how wide the bright front is, and how far behind it the grid stays bright.
const float CELLS = 4.0;
const float FRONT = 1.4;
const float TRAIL = 7.0;

float hash3(vec3 p) {
    p = fract(p * vec3(123.34, 456.21, 789.53));
    p += dot(p, p.yzx + 45.32);
    return fract((p.x + p.y) * p.z);
}

void main() {
    float seconds = GameTime * 1200.0;

    // The light runs out from the cube as a sphere: not here yet, nothing; right behind its front, a bright band;
    // further behind, the grid it left on the room, dimming.
    float behind = radius - length(localPos);
    if (behind < 0.0) {
        discard;
    }
    float front = 1.0 - smoothstep(0.0, FRONT, behind);
    float edge = 1.0 - smoothstep(0.0, 0.22, behind);

    vec2 cellPos = faceCoord * CELLS;
    vec2 inCell = fract(cellPos);
    vec2 width = fwidth(cellPos) * 1.2;
    vec2 lines = 1.0 - smoothstep(vec2(0.0), width, min(inCell, 1.0 - inCell));
    float line = max(lines.x, lines.y);
    vec3 cell = floor(localPos * CELLS + 0.013);
    float twinkle = step(0.9, hash3(cell + floor(seconds * 6.0)));
    float trail = max(1.0 - smoothstep(0.0, TRAIL, behind), 0.3);

    float alpha = edge * 0.5 + front * 0.16 + line * (0.24 + 0.55 * front) * trail + twinkle * front * 0.4 + flash * (0.14 + line * 0.55);
    alpha *= fade;
    if (alpha < 0.004) {
        discard;
    }
    vec3 colour = mix(CYAN, vec3(0.85, 1.0, 1.0), clamp(edge * 0.45 + flash * 0.4, 0.0, 1.0));
    fragColor = vec4(colour, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
