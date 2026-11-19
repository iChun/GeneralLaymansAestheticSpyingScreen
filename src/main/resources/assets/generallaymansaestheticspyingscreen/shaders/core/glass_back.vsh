#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The back of a screen or the inside of a cloak field. The vertex carries:
//   Color.r: 0 for the surface, 1 for its edges and corner marks; Color.b: 1 for a simulation's barrier (always faintly
//   there, seen from far); Color.a: how far it has faded in;
//   UV0: its position on the surface, in blocks (for the nano grid and the ripple);
//   UV1: where the ripple started, on the surface, in 1/32 blocks;
//   UV2: the ripple's radius in 1/64 blocks, and its strength (0..1000).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out float kind;
out float barrier;
out float fade;
out vec2 surface;
out vec3 viewPos;
out vec2 rippleCentre;
out vec2 ripple;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    kind = Color.r;
    barrier = Color.b;
    fade = Color.a;
    surface = UV0;
    viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    rippleCentre = vec2(UV1) / 32.0;
    ripple = vec2(float(UV2.x) / 64.0, float(UV2.y) / 1000.0);
}
