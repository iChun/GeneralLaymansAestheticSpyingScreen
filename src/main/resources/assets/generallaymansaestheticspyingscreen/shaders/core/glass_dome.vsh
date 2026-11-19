#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The dome round a holo cube's simulation: a box round the room, on which the scan's panorama is looked up by the
// direction from the eye (so it looks endlessly far away, like the sky). Position is already from the eye, in the
// world's directions. Color.a: how far it has faded in.

in vec3 Position;
in vec4 Color;

out vec3 direction;
out float fade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    direction = Position;
    fade = Color.a;
}
