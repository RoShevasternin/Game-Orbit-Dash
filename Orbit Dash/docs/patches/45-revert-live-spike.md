# 45 — Відкат живого шипа (патч 44) → постійний пульс (патч 42)

## Що і навіщо

На великих швидкостях анімацію променів не видно, тож шейдерний шип не
вартий складності. Повертаємо стан патча 42: MSDF-зірка, повільний оберт за
годинниковою і удар раз на 1.25 с.

Чернетки `43-live-spike-draft.md` і `44-live-spike.md` лишаються в `docs/` як
історія: там шейдер і стенд, якщо колись знадобиться.

## Стан зараз

- `actors/objects/ASpike.kt` — **уже повернуто** до версії патча 42 (код із
  `42-spike-heavy-beat.md`, «Файл цілком»).
- `GameScreen.kt` — ще з правками патча 44, тому збірка **не проходить**:
  ```
  GameScreen.kt:362  Unresolved reference 'watchBall'
  GameScreen.kt:386  Unresolved reference 'spawn'
  ```
- `SpikeEffect.kt` і `spikeFS.glsl` — ще лежать, їх ніхто не імпортує.

Компілюється після пункту 1. Пункт 2 — прибирання.

---

## 1. `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

### 1а. `syncEntities()`, ВИДАЛИТИ два шматки

Було:
```kotlin
    private fun syncEntities() {
        seenIds.clear()

        // Шипи стежать за м'ячем — їм іде лише позиція, реагують вони самі (ASpike.watchBall)
        val ballX = aBall.x + aBall.width  / 2f
        val ballY = aBall.y + aBall.height / 2f

        for (e in engine.entities) {
            seenIds.add(e.id)
            val actor = activeActors.getOrPut(e.id) { acquire(e) }

            aOrbitField.positionAt(actor, e.rr * RunEngine.TO_FIELD, -e.a)
            actor.color.a = e.s   // spawn-fade 0→1

            if (actor is ASpike) actor.watchBall(ballX, ballY)
        }

        releaseMissing()
    }
```
Стало:
```kotlin
    private fun syncEntities() {
        seenIds.clear()

        for (e in engine.entities) {
            seenIds.add(e.id)
            val actor = activeActors.getOrPut(e.id) { acquire(e) }

            aOrbitField.positionAt(actor, e.rr * RunEngine.TO_FIELD, -e.a)
            actor.color.a = e.s   // spawn-fade 0→1
        }

        releaseMissing()
    }
```

### 1б. `acquire()`, рядок шипа — ЗАМІНИТИ два рядки одним

Було:
```kotlin
        RunEngine.Kind.SPIKE -> (freeSpikes.removeLastOrNull() ?: ASpike(this).also { prepare(it, SPIKE_SIZE) })
            .also { it.spawn(e.id) }
```
Стало:
```kotlin
        RunEngine.Kind.SPIKE -> freeSpikes.removeLastOrNull()  ?: ASpike(this).also { prepare(it, SPIKE_SIZE) }
```

## 2. ВИДАЛИТИ файли

```bash
rm app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/SpikeEffect.kt
rm -r app/src/main/assets/shader/objects
```

(з `Orbit Dash/`; у `shader/objects/` нічого, крім `spikeFS.glsl`)

## Перевірка

`./gradlew :app:compileDebugKotlin`: після пункту 1 має пройти.

---

## Застосовано — 17 вересня 2026

Зробив Claude із разового дозволу. `deny` у `.claude/settings.json` зняв на час
відкату й повернув одразу після (файл знову збігається з комітом).

- `GameScreen.kt`: обидві правки прибрано, файл збігається з комітом;
- `SpikeEffect.kt` і `assets/shader/objects/` видалено;
- `:app:compileDebugKotlin --rerun-tasks` — BUILD SUCCESSFUL.
