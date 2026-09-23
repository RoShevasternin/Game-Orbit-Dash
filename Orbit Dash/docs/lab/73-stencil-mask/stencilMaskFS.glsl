#ifdef GL_ES
precision mediump float;
#endif

// Запис маски у стенсил. Кольору не пише (glColorMask вимкнено на час проходу) —
// шейдер потрібен лише щоб ВІДКИНУТИ прозорі текселі: без discard стенсил отримав би
// увесь квад, а не форму. Поріг 0.5 — край маски твердий, без напівтонів.
varying vec2 v_texCoords;
uniform sampler2D u_texture;

void main() {
    if (texture2D(u_texture, v_texCoords).a < 0.5) discard;
    gl_FragColor = vec4(0.0);
}
