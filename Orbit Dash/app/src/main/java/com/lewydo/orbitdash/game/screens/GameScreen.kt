package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.lewydo.orbitdash.game.actors.background.AComet
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.ADebugPanel
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugPanel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.objects.ABall
import com.lewydo.orbitdash.game.actors.objects.ABooster
import com.lewydo.orbitdash.game.actors.objects.AGem
import com.lewydo.orbitdash.game.actors.objects.ASpike
import com.lewydo.orbitdash.game.actors.orbit.AOrbitField
import com.lewydo.orbitdash.game.actors.panel.APanelGameHud
import com.lewydo.orbitdash.game.engine.RunEngine
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animHide
import com.lewydo.orbitdash.game.utils.actor.animShow
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.util.log

// ----------------------------------------------------------------------------
//  НЕБО ПЕРЕЖИВАЄ ПЕРЕХІД САМЕ ТОМУ, ЩО МИ ЙОГО НЕ ЧІПАЄМО.
//  aStarField/aComet живуть на stageUI, поза rootConstraintLayout, а
//  StarFieldClock/ShaderClock глобальні — тож поле продовжує кадр меню.
//
//  РОЗПОДІЛ РОЛЕЙ НА ЦЬОМУ ЕКРАНІ:
//    RunEngine   — ЩО відбувається (кути, радіуси, колізії, рахунок)
//    AOrbitField — ДЕ це намалювати (геометрія кілець і посадка)
//    GameScreen  — місток: годує рушій часом, перекладає стан в акторів
//
//  Екран НЕ приймає ігрових рішень. Якщо тут з'явиться «if спайк близько» —
//  це знак, що логіка тече не туди.
// ----------------------------------------------------------------------------
class GameScreen : AdvancedScreen() {

    companion object {
        private const val TIME_SHOW = 0.35f
        private const val TIME_HIDE = 0.25f

        /** Поле = 320 з 360 екранних design-юнітів, як у Figma-мокапі. */
        private const val FIELD_SIZE = 320f

        /** Розмір ігрових об'єктів у ДИЗАЙНІ ЕКРАНА (не поля). */
        private const val BALL_SIZE   = 22f
        private const val GEM_SIZE    = 18f
        private const val SPIKE_SIZE  = 25f
        private const val BOOST_SIZE  = 13f

        /** Скільки акторів кожного типу тримати напоготові. */
        private const val POOL_GEMS   = 14
        private const val POOL_SPIKES = 14
        private const val POOL_BOOSTS = 3
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aPanelGameHud = APanelGameHud(this)

    private val aStarField by lazy { AStarField(this) }
    private val aComet     by lazy { AComet(this) }

    private val aOrbitField by lazy { AOrbitField(this) }
    private val aBall       by lazy { ABall(this) }

    // ------------------------------------------------------------------------
    // Engine
    // ------------------------------------------------------------------------
    private var engine = RunEngine(RunEngine.Config())

    /**
     * Мета-стан МІЖ ранами — рушій його не знає й не повинен.
     * Дві швидкі смерті (<15с) вмикають mercy наступного рану.
     */
    private var quickDeaths    = 0
    private var debugOrbit3    = false
    /** DEBUG: множник часу для рушія. x0.25 — розглядати near-miss «під лупою». */
    private var debugTimeScale = 1f

    // ------------------------------------------------------------------------
    // Pools
    // ------------------------------------------------------------------------
    //
    //  За хвилину рану крізь екран проходить ~150 сутностей. Створювати й
    //  викидати акторів на кожну — GC-смикання саме там, де потрібна рівна
    //  кадрова. Тому актори живуть увесь ран, а рушій і вид звʼязані через
    //  Entity.id: стабільний ключ, поки сутність жива.
    //
    private val activeActors = HashMap<Int, Actor>()
    private val seenIds      = HashSet<Int>()

    private val freeGems   = ArrayList<AGem>(POOL_GEMS)
    private val freeSpikes = ArrayList<ASpike>(POOL_SPIKES)
    private val freeBoosts = ArrayList<ABooster>(POOL_BOOSTS)

    // ------------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------------
    private val aDebugPanel by lazy {
        ADebugPanel(this, listOf(
            ADebugPanel.Item("RESTART") { startRun() },
            ADebugPanel.Item("+SHIELD") { engine.debugSpawnBoost(RunEngine.Boost.SHIELD) },
            ADebugPanel.Item("ORBIT III") { btn ->
                debugOrbit3 = !debugOrbit3
                btn.label.setText(if (debugOrbit3) "O3: ON" else "ORBIT III")
                startRun()
            },
            ADebugPanel.Item("WIDE 22..145") { btn ->
                // Пресет «зараховувати сусіднє кільце»: різниця кілець 130,
                // тож 145 накриває спайк на сусідній орбіті.
                val wide = engine.nearMax < 100f
                engine.nearMin = if (wide) 22f else 26f
                engine.nearMax = if (wide) 145f else 90f
                btn.label.setText(if (wide) "WIDE: ON" else "WIDE 22..145")
            },
            ADebugPanel.Item("TIME x0.25") { btn ->
                // Слоу-мо ВСЬОГО рушія (dt на вході). Кутова геометрія вікна
                // near-miss від цього не змінюється — лише час на реакцію ×4.
                debugTimeScale = if (debugTimeScale < 1f) 1f else 0.25f
                btn.label.setText(if (debugTimeScale < 1f) "TIME: ON" else "TIME x0.25")
            },
        ))
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()
        startRun()
    }

    override fun render(delta: Float) {
        super.render(delta)

        engine.update(delta * debugTimeScale)

        syncField()
        syncPlayer()
        syncEntities()
        syncHud()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
        addAndFillActor(aComet)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        addGameField()
        addHud()

        addDebugHud(ADebugHud(this@GameScreen))
        addDebugPanel(aDebugPanel)
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        when (engine.phase) {
            RunEngine.Phase.RUN  -> engine.tap()
            // TEMP: поки немає GameOver-панелі — тап рестартить
            RunEngine.Phase.DEAD -> startRun()
        }
        return false
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.color.a = 0f
        rootConstraintLayout.animShow(TIME_SHOW) { blockEnd() }
    }

    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.animHide(TIME_HIDE) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------

    /** Рахунок, геми, комбо, піпси щита, boost progress. */
    private fun AConstraintLayout.addHud() {
        aPanelGameHud.setSize(332f, 70f)
        add(aPanelGameHud) { centerX(); topToTop(margin = 17f) }
    }

    /**
     * Поле — КВАДРАТ 320×320 design (з 360 ширини екрана), тобто обрізане
     * рівно по зовнішній орбіті. Усередині поля власна система координат
     * з DESIGN_W = 320, тож радіуси там — числа з макета один-в-один.
     */
    private fun AConstraintLayout.addGameField() {
        aOrbitField.setSize(FIELD_SIZE, FIELD_SIZE)
        add(aOrbitField) { centerX(); topToBottom(aPanelGameHud, 36f)}

        addBall()
    }

    /** Гравець живе ВСЕРЕДИНІ поля: його позиція — це просто кут і радіус. */
    private fun addBall() {
        aBall.setSize(BALL_SIZE, BALL_SIZE)
        // ХОВАЄМО до першої синхронізації. super.render() малює сцену ПЕРШИМ, а
        // syncPlayer() біжить після нього — тож на перший кадр актор мав би
        // дефолтну позицію (0,0), тобто лівий нижній кут поля. Саме це й було
        // видно як спалах м'яча при відкритті екрана.
        aBall.isVisible = false
        aOrbitField.addActor(aBall)
    }

    // ------------------------------------------------------------------------
    // Run
    // ------------------------------------------------------------------------

    /**
     * Новий ран. Config збирається ТУТ — рушій не читає сейв, усе, що на нього
     * впливає, передається явно. Тому ран відтворюваний: Config + seed + тапи.
     */
    private fun startRun() {
        releaseAllActors()

        engine = RunEngine(RunEngine.Config(
            orbit3 = debugOrbit3,
            mercy  = quickDeaths >= 2,
            // TODO: апгрейди й startBoost — коли розширимо PlayerData
        ))
        if (quickDeaths >= 2) quickDeaths = 0

        engine.listener = runListener
        aPanelGameHud.reset()     // ← НОВЕ

        // Кільця стартують у позиції рушія без лерпу — інакше перший кадр
        // показав би стару розкладку і смикнув би її на місце.
        syncField()
        gdxGame.analytics.runStart()
    }

    private val runListener = object : RunEngine.Listener {
        override fun onDied(result: RunEngine.RunResult) {
            quickDeaths = if (result.durationSec < 15) quickDeaths + 1 else 0

            gdxGame.analytics.runEnd(
                score       = result.score,
                durationSec = result.durationSec,
                gemsEarned  = result.gems,
                deathRing   = result.deathRing,
                newBest     = false,   // TODO: порівняти з PlayerData.best
            )

            // TODO: GameOver-панель (score, +gems, REVIVE·AD, X2·AD, RESTART, MENU)
            log("DEAD score=${result.score} gems=${result.gems} t=${result.durationSec}s ring=${result.deathRing}")
        }

        override fun onOrbit3Online() { log("ORBIT III ONLINE") }
        override fun onNearMiss(e: RunEngine.Entity, bonus: Int) { log("CLOSE! +$bonus") }
        override fun onBoostApplied(boost: RunEngine.Boost, e: RunEngine.Entity?) { log("BOOST $boost") }
        override fun onShieldSaved(e: RunEngine.Entity) { log("SHIELD SAVED") }
        // TODO: звуки, партикли, вібро — кожен у своєму колбеку
    }

    // ------------------------------------------------------------------------
    // Sync · рушій → вид
    // ------------------------------------------------------------------------

    /** Кільця беруть геометрію з рушія: малюємо рівно те, по чому колізії. */
    private fun syncField() {
        aOrbitField.syncFrom(
            ringR   = engine.ringR,
            count   = engine.ringCount,
            active  = engine.ringIndex,
            r3Alpha = engine.ring3Alpha,
        )
    }

    /**
     * МІНУС кута — переклад із системи рушія (Y-вниз, як у прототипі) в
     * екранну (Y-вгору). Радіус — через TO_FIELD у design-юніти поля.
     * Ці два перетворення і є ВСЯ межа між рушієм і видом.
     */
    private fun syncPlayer() {
        // Поле ще не пройшло layout (width == 0) — позиція була б фальшивою.
        // Тримаємо м'яч схованим до першого чесного кадру.
        if (aOrbitField.width <= 0f) { aBall.isVisible = false; return }

        aOrbitField.positionAt(aBall, engine.radius * RunEngine.TO_FIELD, -engine.angle)
        aBall.rotation = -engine.angle   // мінус — той самий переклад Y-вниз → Y-вгору, що й для позиції
        aBall.isVisible = true

        // Невразливість після щита — блимання
        aBall.color.a = if (engine.invuln > 0f && (engine.invuln * 10f).toInt() % 2 == 0) 0.35f else 1f
    }

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

    /**
     * Геми показуємо СУМОЮ: баланс гравця плюс незараховані за цей ран.
     * Гравець має бачити, скільки в нього СТАНЕ, а не скільки було до старту —
     * інакше підбір гема нічого не міняє на екрані й читається як баг.
     */
    private fun syncHud() {
        aPanelGameHud.syncFrom(
            engine    = engine,
            gemsTotal = gdxGame.modelPlayer.gems + engine.gemCount,
        )
    }

    // ------------------------------------------------------------------------
    // Pool
    // ------------------------------------------------------------------------
    private fun acquire(e: RunEngine.Entity): Actor = when (e.kind) {
        RunEngine.Kind.GEM   -> freeGems.removeLastOrNull()    ?: AGem(this).also { prepare(it, GEM_SIZE) }
        RunEngine.Kind.SPIKE -> freeSpikes.removeLastOrNull()  ?: ASpike(this).also { prepare(it, SPIKE_SIZE) }
        RunEngine.Kind.BOOST -> (freeBoosts.removeLastOrNull() ?: ABooster(this).also { prepare(it, BOOST_SIZE) })
            .also { it.boost = e.boost ?: RunEngine.Boost.MAGNET }
    }.also { it.isVisible = true }

    private fun prepare(actor: Actor, size: Float) {
        actor.setSize(size, size)
        aOrbitField.addActor(actor)
    }

    /** Сутність зникла в рушії (підібрана / пішла за спину) — актор у пул. */
    private fun releaseMissing() {
        val it = activeActors.entries.iterator()
        while (it.hasNext()) {
            val (id, actor) = it.next()
            if (id in seenIds) continue

            actor.isVisible = false
            when (actor) {
                is AGem      -> freeGems.add(actor)
                is ASpike    -> freeSpikes.add(actor)
                is ABooster  -> freeBoosts.add(actor)
            }
            it.remove()
        }
    }

    /** Новий ран — усі актори назад у пул, id старого рану більше не існують. */
    private fun releaseAllActors() {
        seenIds.clear()
        releaseMissing()
    }

}