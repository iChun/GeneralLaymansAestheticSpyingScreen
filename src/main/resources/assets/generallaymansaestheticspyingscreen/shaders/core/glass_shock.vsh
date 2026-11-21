#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A cloak field's shockwave: glitchy static over the field that clears in a sphere from where someone went through it.
// The vertex carries:
//   UV0: this point on its face, in blocks, from the point the shock started (both measured along the face);
//   UV1.x: how far the face's plane is from that point, in 1/32 blocks;
//   UV2.x: the shock's radius in 1/64 blocks; UV2.y: how far it has faded out (0..1000);
//   Color.a: 1 seen from inside the field (the static covers what was hidden), lower from outside.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 surface;
out float depth;
out float radius;
out float life;
out float inside;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surface = UV0;
    depth = float(UV1.x) / 32.0;
    radius = float(UV2.x) / 64.0;
    life = float(UV2.y) / 1000.0;
    inside = Color.a;
}
