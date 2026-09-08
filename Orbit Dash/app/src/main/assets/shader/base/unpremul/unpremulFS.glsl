#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// UNPREMUL — повернути пряму альфу після ланцюга post-ефектів.
//
// Усередині VfxTexture ланцюг працює з премультиплікованою альфою (інакше
// блюр дає темну облямівку). Але споживачі — звичайні Image зі звичайним
// SRC_ALPHA blending, їм потрібна пряма. Один прохід на зміну, не на кадр.
// ─────────────────────────────────────────────────────────────────────────────

varying vec2 v_texCoords;
uniform sampler2D u_texture;

void main() {
    vec4 c = texture2D(u_texture, v_texCoords);
    gl_FragColor = vec4(c.rgb / max(c.a, 1.0e-4), c.a);
}