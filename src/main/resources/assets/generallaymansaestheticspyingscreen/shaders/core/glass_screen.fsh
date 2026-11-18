#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
#ifdef WIPE
// The old image: while a screen switches feed, it shows outside the wave and the new image wipes over it.
uniform sampler2D Sampler1;
#endif

in vec4 texProj0;
in vec2 texCoord0;
in vec4 vertexColor;
in vec4 punch;

out vec4 fragColor;

// Nano pixels per block (a vanilla texel is 1/16 block; these are 2.5 times bigger).
const float CELLS = 6.4;
// How far behind the wave front the effect lasts, in blocks.
const float FRONT_WIDTH = 1.3;
// How far single pixels may spill out ahead of the (round) wave front, in blocks.
const float SPILL = 0.45;
// Width of the punch ring, in blocks.
const float RING_WIDTH = 0.9;

// Cheap hash noise: one random value per nano pixel.
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    // texCoord0: where this point is on the screen, in blocks from the projector. Red + green: the radius of the wave
    // around the projector (16 bits, 0..256 blocks).
    float radius = (vertexColor.r * 255.0 * 256.0 + vertexColor.g * 255.0) / 65535.0 * 256.0;
    float seconds = GameTime * 1200.0;

    // One nano-pixel grid across the whole screen. A pixel is there once the round wave has passed it; a little noise
    // lets a few pixels spill out ahead of it. Switching on, the wave grows out of the projector; switching off, it
    // shrinks back into it.
    vec2 grid = texCoord0 * CELLS;
    vec2 cell = floor(grid);
    vec2 centre = (cell + 0.5) / CELLS;
    float noise = hash(cell) - 0.5;
    float reveal = radius - length(centre) + noise * SPILL * 2.0;
    if (reveal < 0.0) {
#ifdef WIPE
        if (vertexColor.b > 0.5) {
            fragColor = vec4(texture(Sampler1, texProj0.xy / texProj0.w).rgb, 1.0) * ColorModulator;
            return; // the wave has not reached this pixel yet: the old image is still there
        }
#endif
        discard; // the wave has not reached this pixel: plain glass shows through
    }
    float front = 1.0 - smoothstep(0.0, FRONT_WIDTH, reveal);

    // A punch: a ring that travels out from where the glass was hit, over the whole screen, fading as it goes.
    vec2 fromHit = centre - punch.xy;
    float hitDistance = length(fromHit);
    float ring = punch.w * (1.0 - smoothstep(0.0, RING_WIDTH, abs(hitDistance - punch.z)));

    float amount = max(front, ring);

    // Refraction: bend the camera image like thick moving glass, with a prism-like colour split. The punch ring
    // pushes the image outwards, like a ripple on water.
    vec2 wave = vec2(sin(texCoord0.y * 3.0 + seconds * 6.0), cos(texCoord0.x * 3.0 - seconds * 5.0));
    vec2 jitter = vec2(hash(cell + 1.7), hash(cell + 9.2)) - 0.5;
    vec2 outwards = hitDistance > 0.001 ? fromHit / hitDistance : vec2(0.0);
    vec2 offset = (wave * 0.010 + jitter * 0.025) * front + outwards * vec2(0.03, -0.03) * ring;
    vec2 uv = texProj0.xy / texProj0.w;
    vec3 colour;
    colour.r = texture(Sampler0, uv + offset * 1.4).r;
    colour.g = texture(Sampler0, uv + offset).g;
    colour.b = texture(Sampler0, uv + offset * 0.6).b;

    // The wave front and the punch ring saturate and brighten the image itself (no added colour): a vivid, glowing
    // band that settles into the normal image behind it. A few pixels flicker brighter while it moves.
    float spark = step(0.85, hash(cell + floor(seconds * 14.0)));
    float luma = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = mix(vec3(luma), colour, 1.0 + amount * 1.6);
    colour *= 1.0 + amount * 0.45 + spark * front * 0.35;
    colour = clamp(colour, 0.0, 1.0);

    fragColor = vec4(colour, 1.0) * ColorModulator;
}
