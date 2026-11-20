#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>


in float fade;
in vec2 wall;

out vec4 fragColor;

// The dome round a holo cube's simulation, seen from inside: the real world shut out by a black void, so all there is
// is the simulation. At the foot of its walls the void meets the simulation's ground in the BARF edge: glowing blue
// fabric, pixel by pixel, flickering on its own beat and thinning out up the wall; here and there a pixel higher up and
// overhead too, like distant lights.
const vec3 VOID = vec3(0.004, 0.007, 0.014);
// Nano pixels per block (bigger than on the room itself: the dome is far away), and how high the edge's band reaches.
const float CELLS = 2.5;
const float EDGE_BAND = 3.5;

float hash2(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float seconds = GameTime * 1200.0;
    vec2 cellPos = wall * CELLS;
    vec2 cell = floor(cellPos);
    float near = 1.0 - smoothstep(0.0, EDGE_BAND, wall.y);
    float own = hash2(cell + 17.0);
    float beat = hash2(cell + floor(seconds * (0.5 + own * 1.5) + own * 7.0));
    float keep = own - (near * near * 0.8 + (beat - 0.5) * 0.3 * near);
    bool stray = hash2(cell + 3.7) < 0.008 && beat > 0.55;
    vec3 colour = VOID;
    if (keep < 0.0 || stray) {
        vec2 inCell = fract(cellPos);
        vec2 toEdge = min(inCell, 1.0 - inCell);
        float gridLine = 1.0 - smoothstep(0.0, 0.12, min(toEdge.x, toEdge.y));
        float breathe = 0.55 + 0.45 * sin(seconds * (1.5 + own * 2.5) + own * 40.0);
        colour = vec3(0.03, 0.16, 0.30) + vec3(0.18, 0.62, 1.0) * (0.25 + 0.35 * breathe) + vec3(0.45, 0.9, 1.0) * gridLine * 0.6;
        if (stray && keep >= 0.0) {
            colour *= 0.6;
        }
    } else if (near > 0.0) {
        // The pixels about to go glow; the rest of the band is a deep blue haze over the void.
        float glow = 1.0 - smoothstep(0.0, 0.12, keep);
        colour = mix(VOID, vec3(0.02, 0.08, 0.18), near) + vec3(0.40, 0.85, 1.0) * glow * near;
    }
    fragColor = vec4(colour, fade) * ColorModulator;
}
