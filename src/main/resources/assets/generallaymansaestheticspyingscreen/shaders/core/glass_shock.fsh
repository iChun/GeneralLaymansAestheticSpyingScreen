#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec2 surface;
in float depth;
in float radius;
in float life;
in float inside;

out vec4 fragColor;

// Nano pixels per block, as on the screens.
const float CELLS = 6.4;
// Width of the bright front, and how far single pixels may lag behind it or run ahead, in blocks.
const float FRONT = 1.1;
const float SPILL = 0.6;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float seconds = GameTime * 1200.0;
    float tick = floor(seconds * 24.0);

    // Glitch: now and then a row of pixels jumps sideways (and tints magenta).
    float row = floor(surface.y * CELLS);
    float jump = step(0.82, hash(vec2(row, tick))) * (hash(vec2(row + 3.1, tick)) - 0.5) * 1.6;
    vec2 grid = (surface + vec2(jump, 0.0)) * CELLS;
    vec2 cell = floor(grid);
    vec2 centre = (cell + 0.5) / CELLS - vec2(jump, 0.0);

    // Where the shock is: a sphere around where it started. Inside it the static is gone.
    float distance = length(vec3(centre, depth));
    float ahead = distance - radius + (hash(cell) - 0.5) * SPILL * 2.0;
    if (ahead < 0.0) {
        discard;
    }
    float front = 1.0 - smoothstep(0.0, FRONT, ahead);

    // Static: mostly dark pixels, some cyan, a few white, flickering.
    vec3 cyan = vec3(0.24, 0.9, 1.0);
    float flicker = hash(cell + tick * 0.37);
    vec3 colour = mix(vec3(0.02, 0.05, 0.08), cyan * 0.7, step(0.72, flicker));
    colour = mix(colour, vec3(1.0), step(0.97, flicker));
    colour += vec3(0.6, 0.0, 0.5) * step(0.001, abs(jump)) * 0.35;
    // Faint grid lines between the pixels.
    vec2 inCell = fract(grid);
    colour = mix(colour, cyan, min(step(inCell.x, 0.08) + step(inCell.y, 0.08), 1.0) * 0.25);
    // The front: a bright cyan band.
    colour = mix(colour, cyan * 1.6, front);

    float fadeOut = 1.0 - life;
    float alpha = max((inside > 0.75 ? 0.82 : 0.55), front) * fadeOut;
    fragColor = vec4(min(colour, vec3(1.0)), alpha) * ColorModulator;
}
