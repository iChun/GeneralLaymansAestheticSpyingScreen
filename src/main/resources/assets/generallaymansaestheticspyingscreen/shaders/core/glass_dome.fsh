#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec3 direction;
in float fade;

out vec4 fragColor;

// The panorama: six views three across and two down, as Panoramas takes them: south, west, north, east, up, down.
// Each: the way it looks (forward), its right and its up, as the game's camera has them.
const vec3 FORWARD[6] = vec3[6](vec3(0, 0, 1), vec3(-1, 0, 0), vec3(0, 0, -1), vec3(1, 0, 0), vec3(0, 1, 0), vec3(0, -1, 0));
const vec3 RIGHT[6] = vec3[6](vec3(-1, 0, 0), vec3(0, 0, -1), vec3(1, 0, 0), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(-1, 0, 0));
const vec3 UP[6] = vec3[6](vec3(0, 1, 0), vec3(0, 1, 0), vec3(0, 1, 0), vec3(0, 1, 0), vec3(0, 0, -1), vec3(0, 0, 1));
const float FACE_TEXELS = 1024.0;

void main() {
    vec3 d = normalize(direction);
    int face = 0;
    float best = -2.0;
    for (int i = 0; i < 6; i++) {
        float along = dot(d, FORWARD[i]);
        if (along > best) {
            best = along;
            face = i;
        }
    }
    vec2 onFace = vec2(dot(d, RIGHT[face]), dot(d, UP[face])) / best;
    vec2 uv = vec2(onFace.x + 1.0, 1.0 - onFace.y) * 0.5;
    // Keep half a texel off the edges so neighbouring views do not bleed in.
    uv = clamp(uv, vec2(0.5 / FACE_TEXELS), vec2(1.0 - 0.5 / FACE_TEXELS));
    vec2 cell = vec2(float(face - (face / 3) * 3), float(face / 3));
    vec3 colour = texture(Sampler0, (cell + uv) / vec2(3.0, 2.0)).rgb;

    // A hint that it is a simulation, kept light so the panorama itself reads clearly: a touch of blue, faint scanlines.
    float seconds = GameTime * 1200.0;
    float scan = 0.985 + 0.015 * step(0.5, fract(gl_FragCoord.y / 4.0 - seconds * 0.8));
    colour = mix(colour, colour * vec3(0.82, 0.95, 1.12), 0.15) * scan;
    fragColor = vec4(colour, fade) * ColorModulator;
}
