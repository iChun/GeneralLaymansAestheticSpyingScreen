#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 texProj0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = textureProj(Sampler0, texProj0);
    fragColor = vec4(color.rgb, 1.0) * vertexColor * ColorModulator;
}
