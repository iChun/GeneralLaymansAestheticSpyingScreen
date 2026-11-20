#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The dome round a holo cube's simulation: a box round the room, shutting the real world out (see glass_dome.fsh).
// Color.a: how far it has faded in. UV0: where on its wall the point is, in blocks: along the wall, and how high above
// the ground the cube stands on (very high on the roof and floor).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out float fade;
out vec2 wall;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fade = Color.a;
    wall = UV0;
}
