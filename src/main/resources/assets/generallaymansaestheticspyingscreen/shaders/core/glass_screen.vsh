#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 texProj0;
out vec2 texCoord0;
out vec4 vertexColor;
out vec4 punch;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // Screen-space coordinates, so the terminal's view lines up with where the glass is on screen.
    texProj0 = projection_from_position(gl_Position);
#ifdef REPROJECT
    // A cloak's view may be a few frames old and cover only part of the screen: TextureMat takes this point (relative
    // to the camera now) to where it was in that image. All zero: the image is screen-space after all (it is being
    // drawn into another view right now).
    if (TextureMat[3][3] != 0.0 || TextureMat[0][0] != 0.0) {
        texProj0 = projection_from_position(TextureMat * vec4(Position, 1.0));
    }
#endif
    // Position on the screen in blocks from the projector, for the wave and the nano-pixel grid.
    texCoord0 = UV0;
    // Red + green: wave radius (16 bits).
    vertexColor = Color;
    // Punch ripple: where it hit (xy, blocks from the projector), ring radius (blocks) and strength (0..1).
    punch = vec4(vec2(UV1) / 32.0, float(UV2.x) / 64.0, float(UV2.y) / 1000.0);
}
