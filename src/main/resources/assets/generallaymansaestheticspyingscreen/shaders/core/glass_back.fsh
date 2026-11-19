#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in float kind;
in float barrier;
in float fade;
in vec2 surface;
in vec3 viewPos;
in vec2 rippleCentre;
in vec2 ripple;

out vec4 fragColor;

// Nano pixels per block (the same as the screens), and how close the eye has to come before the grid shows.
const float CELLS = 6.4;
const float GRID_FROM = 4.5;
const float GRID_FULL = 1.2;
const vec3 CYAN = vec3(0.30, 0.86, 1.0);

float hash2(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float seconds = GameTime * 1200.0;
    float eyeDistance = length(viewPos);

    // The ripple: a ring running out from where something went through or touched it, lighting the grid as it passes.
    float rippleDistance = length(surface - rippleCentre);
    float ring = (1.0 - smoothstep(0.0, 0.45, abs(rippleDistance - ripple.x))) * ripple.y;

    if (kind > 0.5) {
        // Edges and corner marks: always there, so you can see where the screen is from behind, breathing a little.
        float pulse = 0.78 + 0.22 * sin(seconds * 2.2 + (surface.x + surface.y) * 0.7);
        fragColor = vec4(CYAN * (1.0 + ring), clamp(0.62 * pulse + ring * 0.35, 0.0, 1.0) * fade) * ColorModulator;
        return;
    }

    // The nano grid, faded in as the eye comes close (brightest where it is nearest), with a few pixels twinkling.
    float near = 1.0 - smoothstep(GRID_FULL, GRID_FROM, eyeDistance);
    vec2 cellPos = surface * CELLS;
    vec2 cell = floor(cellPos);
    vec2 inCell = fract(cellPos);
    vec2 width = fwidth(cellPos) * 1.2;
    vec2 lines = 1.0 - smoothstep(vec2(0.0), width, min(inCell, 1.0 - inCell));
    float line = max(lines.x, lines.y);
    float own = hash2(cell);
    float twinkle = step(0.93, hash2(cell + floor(seconds * (0.8 + own * 1.6) + own * 9.0)));
    float sweep = step(0.96, fract(cell.y / CELLS * 0.25 - seconds * 0.35));

    float alpha = (line * 0.42 + twinkle * 0.30 + sweep * 0.10) * near;
    // The ripple lights the whole cells it passes, at any distance.
    alpha += ring * (0.18 + line * 0.5 + twinkle * 0.3);
    if (barrier > 0.5) {
        // A simulation's barrier is there from far too: a faint blue sheen, a coarse grid, and bands of light rising.
        vec2 coarsePos = surface * 1.0;
        vec2 coarseIn = fract(coarsePos);
        vec2 coarseWidth = fwidth(coarsePos) * 1.4;
        vec2 coarseLines = 1.0 - smoothstep(vec2(0.0), coarseWidth, min(coarseIn, 1.0 - coarseIn));
        float coarse = max(coarseLines.x, coarseLines.y);
        float rise = fract(surface.y / 5.0 - seconds * 0.12);
        float band = smoothstep(0.0, 0.05, rise) * (1.0 - smoothstep(0.05, 0.22, rise));
        alpha += 0.045 + coarse * 0.16 + band * 0.22 + twinkle * 0.08;
    }
    alpha *= fade;
    if (alpha < 0.004) {
        discard;
    }
    vec3 colour = CYAN * (1.0 + twinkle * 0.4 + ring * 0.6);
    fragColor = vec4(colour, clamp(alpha, 0.0, 0.9)) * ColorModulator;
}
