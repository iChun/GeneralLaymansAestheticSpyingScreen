#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A holo cube scanning the room: a face of the real room the scan's light runs over. The vertex carries:
//   Color.a: how much of the effect is left (it fades once the scan is done); Color.g: the flash when it is done;
//   UV0: the corner of the face (0..1 each way), for the grid;
//   UV1.x, UV2.x, UV1.y: its position from the cube (x, y, z) in 1/32 blocks;
//   UV2.y: how far the light has run, in 1/64 blocks.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 faceCoord;
out vec3 localPos;
out float radius;
out float fade;
out float flash;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    faceCoord = UV0;
    localPos = vec3(float(UV1.x), float(UV2.x), float(UV1.y)) / 32.0;
    radius = float(UV2.y) / 64.0;
    fade = Color.a;
    flash = Color.g;
}
