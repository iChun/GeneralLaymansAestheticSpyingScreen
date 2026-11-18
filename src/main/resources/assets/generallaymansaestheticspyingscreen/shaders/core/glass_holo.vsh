#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A hologram. The vertex carries, besides the block texture:
//   UV1.x, UV2.x, UV1.y: its position from the projector (x, y, z) in 1/32 blocks, for the nano wave and its pixels;
//   UV2.y: the wave's radius in 1/64 blocks;
//   Normal.xy: the corner of the block face (+-0.71 each way), for the glowing edges.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 faceCoord;
out vec3 localPos;
out float waveRadius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    faceCoord = clamp(Normal.xy * 0.7071 + 0.5, 0.0, 1.0);
    localPos = vec3(float(UV1.x), float(UV2.x), float(UV1.y)) / 32.0;
    waveRadius = float(UV2.y) / 64.0;
}
