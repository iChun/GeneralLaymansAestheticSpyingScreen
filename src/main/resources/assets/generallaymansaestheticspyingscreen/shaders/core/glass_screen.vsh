#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec4 texProj0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // Screen-space coordinates, so the terminal's view lines up with where the glass is on screen.
    texProj0 = projection_from_position(gl_Position);
    vertexColor = Color;
}
