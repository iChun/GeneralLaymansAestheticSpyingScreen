#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 faceCoord;
in vec3 localPos;
in float waveRadius;

out vec4 fragColor;

// Nano pixels per block, how far behind the wave front the effect lasts, and how far pixels may spill out ahead of it:
// the same as the GLASS screens (glass_screen.fsh), in 3D.
const float CELLS = 6.4;
const float FRONT_WIDTH = 1.3;
const float SPILL = 0.45;
// A simulation's edge: how far in from its walls it falls apart into blue nano pixels, in blocks (vertex alpha carries
// the distance, see HologramRenderer.EDGE_BAND; alpha 1 means no edge).
const float EDGE_BAND = 1.75;

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

float hash3(vec3 p) {
    p = fract(p * vec3(123.34, 456.21, 789.53));
    p += dot(p, p.yzx + 45.32);
    return fract((p.x + p.y) * p.z);
}

void main() {
    float seconds = GameTime * 1200.0;

    // One wave from the projector, as a sphere. A nano pixel (a little cube of space) is there once the wave has passed
    // it; a little noise lets a few spill out ahead. Switching on it grows, switching off it shrinks back.
    vec3 cell = floor(localPos * CELLS + 0.0137);
    vec3 centre = (cell + 0.5) / CELLS;
    float reveal = waveRadius - length(centre) + (hash3(cell) - 0.5) * SPILL * 2.0;
    if (reveal < 0.0) {
        discard;
    }
    float front = 1.0 - smoothstep(0.0, FRONT_WIDTH, reveal);

    // The edge of a simulation (so you know where it ends): the closer to its walls, the more nano pixels give way to
    // the simulation's own glowing blue fabric, each flickering over and back on its own beat; the ones about to go
    // glow, and the rest turns blue. Right at the outside a few break off altogether.
    float edgeDistance = vertexColor.a > 0.999 ? 1000.0 : vertexColor.a * (255.0 / 254.0) * EDGE_BAND;
    float near = 1.0 - clamp(edgeDistance / EDGE_BAND, 0.0, 1.0);
    float glow = 0.0;
    float fabric = 0.0;
    // Which way this face looks (derivatives: outside any branch), for the fabric's grid lines along it.
    vec3 faceAxis = abs(cross(dFdx(localPos), dFdy(localPos)));
    float own = hash3(cell + 17.0);
    if (near > 0.0) {
        float beat = hash3(cell + floor(seconds * (0.5 + own * 1.5) + own * 7.0));
        float keep = own - (near * near * 0.72 + (beat - 0.5) * 0.3 * near);
        if (keep < 0.0) {
            if (near > 0.86 && keep < -0.25) {
                discard;
            }
            fabric = 1.0;
        }
        glow = 1.0 - smoothstep(0.0, 0.12, abs(keep));
    }

    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }
    vec3 colour = tex.rgb * vertexColor.rgb;
#ifdef BLUE
    // Classic hologram: only the brightness of the texture, in cyan.
    float luma0 = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = vec3(0.20, 0.80, 1.0) * (0.25 + luma0 * 1.2);
#endif

    // The wave front saturates and brightens the image, with a few pixels flickering brighter while it moves.
    float spark = step(0.85, hash3(cell + floor(seconds * 14.0)));
    float luma = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = mix(vec3(luma), colour, 1.0 + front * 1.6);
    colour *= 1.0 + front * 0.45 + spark * front * 0.35;

    if (near > 0.0) {
        // Blue towards the edge, with lines of light sweeping up it now and then.
        float edgeLuma = dot(colour, vec3(0.299, 0.587, 0.114));
        vec3 blue = vec3(0.16, 0.58, 1.0) * (0.35 + edgeLuma * 1.15);
        float shell = smoothstep(0.0, 1.0, near);
        float sweep = step(0.93, fract((floor(localPos.y * CELLS) / CELLS) * 0.35 - seconds * 0.45));
        colour = mix(colour, blue, shell * 0.85);
        colour += vec3(0.40, 0.85, 1.0) * (glow * 0.95 + sweep * 0.45) * near;
        if (fabric > 0.5) {
            // A pixel that gave way: deep blue, breathing, with a light grid line along its edges.
            vec3 inCell = fract(localPos * CELLS + 0.0137);
            vec3 toEdge = min(inCell, 1.0 - inCell);
            vec2 along = faceAxis.x > faceAxis.y && faceAxis.x > faceAxis.z ? toEdge.yz : (faceAxis.y > faceAxis.z ? toEdge.xz : toEdge.xy);
            float gridLine = 1.0 - smoothstep(0.0, 0.12, min(along.x, along.y));
            float breathe = 0.55 + 0.45 * sin(seconds * (1.5 + own * 2.5) + own * 40.0);
            colour = vec3(0.03, 0.16, 0.30) + vec3(0.18, 0.62, 1.0) * (0.25 + 0.35 * breathe) + vec3(0.45, 0.9, 1.0) * gridLine * 0.6;
        }
    }

#ifdef SOLID
    fragColor = vec4(clamp(colour, 0.0, 1.0), 1.0) * ColorModulator;
#else
    // Glowing edges along every block face, scanlines rolling up the screen, and a flicker now and then.
    float edge = min(min(faceCoord.x, 1.0 - faceCoord.x), min(faceCoord.y, 1.0 - faceCoord.y));
    float rim = 1.0 - smoothstep(0.0, 0.07, edge);
    float scan = 0.80 + 0.20 * step(0.5, fract(gl_FragCoord.y / 3.0 - seconds * 1.5));
    float flicker = hash(floor(seconds * 24.0)) > 0.965 ? 0.55 : 1.0;
    colour = colour * scan + vec3(0.30, 0.90, 1.0) * rim * 0.55;
    float alpha = clamp((0.62 + rim * 0.30 + front * 0.25) * flicker, 0.0, 1.0);
    fragColor = vec4(clamp(colour, 0.0, 1.0), alpha) * ColorModulator;
#endif
}
