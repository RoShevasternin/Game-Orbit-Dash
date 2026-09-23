# Лабораторія 73 — маска через стенсил (22.09.2026)

Стенд і числа — `docs/decisions.md` §9, «Маска через стенсил замість FBO». У проєкт не
внесено: у грі немає жодної FBO-маски, яку було б що заміняти.

- `AStencilMask.kt` — група, що клипає дітей стенсилом (без FBO). Пакет `actors/vfx`.
- `stencilMaskFS.glsl` — запис маски у стенсил: `discard` по альфі < 0.5.
- `TestScreen.bench.kt` — стенд: N груп, тап перемикає NONE / AMASK / STENCIL / …_STATIC,
  `PerfMonitor` пише PERF у logcat.

Щоб повторити: `stencil = 8` у `GDXFragment`, лоадер → `TestScreen`, `adb logcat -s OD_DEBUG`.
