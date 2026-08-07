#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_groupSize;
uniform vec2 u_direction;
uniform float u_blurAmount;

void main() {
    vec4 sum = vec4(0.0);

    vec2 tex_offset = (u_direction * u_blurAmount) / (u_groupSize);

    // Точні Gaussian-ваги для 9 семплів (Сума ≈ 1.0)
    sum += texture2D(u_texture, v_texCoords - 4.0 * tex_offset) * 0.0162162162;
    sum += texture2D(u_texture, v_texCoords - 3.0 * tex_offset) * 0.0540540541;
    sum += texture2D(u_texture, v_texCoords - 2.0 * tex_offset) * 0.1216216216;
    sum += texture2D(u_texture, v_texCoords - 1.0 * tex_offset) * 0.1945945946;

    sum += texture2D(u_texture, v_texCoords) * 0.2270270270; // Центр

    sum += texture2D(u_texture, v_texCoords + 1.0 * tex_offset) * 0.1945945946;
    sum += texture2D(u_texture, v_texCoords + 2.0 * tex_offset) * 0.1216216216;
    sum += texture2D(u_texture, v_texCoords + 3.0 * tex_offset) * 0.0540540541;
    sum += texture2D(u_texture, v_texCoords + 4.0 * tex_offset) * 0.0162162162;

    gl_FragColor = sum * v_color;
}