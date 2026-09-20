package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
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
import com.lewydo.orbitdash.game.actors.objects.ASpark
import com.lewydo.orbitdash.game.actors.objects.ASpike
import com.lewydo.orbitdash.game.actors.fx.ABurst
import com.lewydo.orbitdash.game.actors.fx.AWave
import com.lewydo.orbitdash.game.actors.orbit.AOrbitField
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.panel.APanelGameHud
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.actors.debug.ADebugIconBar
import com.lewydo.orbitdash.game.actors.debug.addDebugIconBar
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animHide
import com.lewydo.orbitdash.game.utils.actor.animShow
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
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
        private const val BALL_SIZE     = 22f
        private const val GEM_SIZE      = 18f
        private const val SPIKE_SIZE    = 25f
        private const val SPARK_SIZE    = 4f
        private const val BOOST_SIZE_W  = 22f
        private const val BOOST_SIZE_H  = 25f

        /** Скільки акторів кожного типу тримати напоготові. */
        private const val POOL_GEMS   = 14
        private const val POOL_SPIKES = 14
        private const val POOL_SPARKS = 14   // по одній на шип
        private const val POOL_BOOSTS = 3

        // ── спливний напис над точкою події («COMBO x3») ──
        //  Порт pop() з прототипу: 26px на канві 720 при полі 640 = 13 юнітів у
        //  нашому полі 320; підйом 50 px/с = 25 юнітів/с; життя 1/0.9 с.
        /** Попапів одночасно. Комбо частіше за раз на 0.2 с не буває. */
        private const val POOL_FLOATS = 4
        private const val FLOAT_SIZE  = 13f    // кегль
        private const val FLOAT_BOX_W = 120f   // коробка ширша за напис — центрується по точці
        private const val FLOAT_BOX_H = 18f
        private const val FLOAT_LIFE  = 1.11f  // с
        private const val FLOAT_RISE  = 28f    // юнітів за все життя (25/с × 1.11)

        // ── спалах у точці спійманої іскри (хвиля + крапки) ──
        //  Обидва живуть менше за напис (0.4 і до 0.9 с проти 1.11), тому їх
        //  треба менше: три комбо поспіль за 0.4 с — це вже не гра, а EZ COMBO.
        private const val POOL_WAVES  = 3
        private const val POOL_BURSTS = 3
        /** Квад бурста — точка: крапки малюються за межами, як glow в об'єктах. */
        private const val BURST_SIZE  = 8f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aPanelGameHud = APanelGameHud(this)

    private val aStarField by lazy { AStarField(this) }
    private val aComet     by lazy { AComet(this) }

    private val aOrbitField by lazy { AOrbitField(this) }
    private val aBall       by lazy { ABall(this) }

    // Спливні написи над полем («COMBO x3»). Не пул із поверненням: мітка сама
    // гасне після 1.11 с, а isVisible каже, що її можна взяти під наступну подію.
    // Спалах іскри: хвиля й розліт крапок. Той самий «пул без повернення», що
    // й написи, — актор сам гасне і сам стає вільним (isVisible == false).
    private val aWaves  by lazy { List(POOL_WAVES)  { AWave(this)  } }
    private val aBursts by lazy { List(POOL_BURSTS) { ABurst(this) } }

    private val styleFloat by lazy { MsdfStyle(gdxGame.msdfManager, gdxGame.msdfManager.fontInter_Bold, FLOAT_SIZE) }
    private val aFloats    by lazy {
        List(POOL_FLOATS) {
            AMsdfLabel("", styleFloat).apply {
                setSize(FLOAT_BOX_W, FLOAT_BOX_H)
                setAlignment(Align.center)
                isVisible = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // Engine
    // ------------------------------------------------------------------------
    private var engine = RunEngine(RunEngine.Config())

    /**
     * Мета-стан МІЖ ранами — рушій його не знає й не повинен.
     * Дві швидкі смерті (<15с) вмикають mercy наступного рану.
     */
    private var quickDeaths    = 0

    /** Скільки комбо цього рану вже закомічено — ревайв фіксує той самий ран удруге. */
    private var comboCommitted = 0

    private var debugOrbit3    = false
    /** DEBUG: множник часу для рушія. x0.25 — розглядати near-miss «під лупою». */
    private var debugTimeScale = 1f
    /** DEBUG · EZ COMBO: комбо за прохід повз шип без іскри, і з сусіднього кільця теж. */
    private var debugEzCombo   = false
    /** DEBUG: м'яч стоїть, решта живе — див. RunEngine.debugFrozen. */
    private var debugPaused    = false

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

    // Іскра — не сутність рушія, а стан шипа: свій пул на id ТОГО Ж шипа.
    // Живе, поки рушій віддає sparkAngle(e) — спіймали або шип зник, і актор у пул.
    private val activeSparks = HashMap<Int, ASpark>()
    private val seenSparkIds = HashSet<Int>()
    private val freeSparks   = ArrayList<ASpark>(POOL_SPARKS)

    // ------------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------------
    private val aDebugPanel by lazy {
        ADebugPanel(this, listOf(
            ADebugPanel.Item("ORBIT III") { btn ->
                // Третя орбіта в ЦЬОМУ рані, одразу — з тим самим роз'їздом, що й у грі.
                // Тримається й у наступних ранах, поки не вимкнеш
                debugOrbit3 = !debugOrbit3
                engine.debugSetOrbit3(debugOrbit3)
                btn.label.setText(if (debugOrbit3) "O3: ON" else "ORBIT III")
            },
            ADebugPanel.Item("EZ COMBO") { btn ->
                // Будь-який прохід повз шип у радіусі 145 — комбо, іскру ловити
                // не треба. Різниця кілець 130, тож накриває й сусідню орбіту.
                debugEzCombo = !debugEzCombo
                engine.debugEzCombo = debugEzCombo
                btn.label.setText(if (debugEzCombo) "COMBO: ON" else "EZ COMBO")
            },
            ADebugPanel.Item("TIME x0.25") { btn ->
                // Слоу-мо ВСЬОГО рушія (dt на вході). Вікно іскри просторове,
                // від цього не змінюється — лише час на реакцію ×4.
                debugTimeScale = if (debugTimeScale < 1f) 1f else 0.25f
                btn.label.setText(if (debugTimeScale < 1f) "TIME: ON" else "TIME x0.25")
            },
            ADebugPanel.Item("PAUSE") { btn ->
                // Стоїть лише м'яч: актори грають свої анімації, підкинуте дограє
                // появу, а колізій немає — шип перед м'ячем не вб'є.
                debugPaused = !debugPaused
                engine.debugFrozen = debugPaused
                btn.label.setText(if (debugPaused) "PAUSE: ON" else "PAUSE")
            },
        ))
    }

    /**
     * Ромби «підкинути»: кожен буст у своєму кольорі й зі своєю іконкою + шип.
     * Розстановку без накладань робить рушій (debugSpawnBoost / debugSpawnSpike).
     */
    private val aDebugIconBar by lazy {
        val boosts = RunEngine.Boost.entries.map { boost ->
            // PNG бустів 105×120 з полями: гліф — приблизно третина висоти
            ADebugIconBar.Item(boost.info.icon, boost.info.color, iconW = 28f, iconH = 32f) {
                engine.debugSpawnBoost(boost)
            }
        }
        val spike = ADebugIconBar.Item(gdxGame.assetsMsdf.spike, ThemeManager.current.spike, iconW = 20f, iconH = 20f) {
            engine.debugSpawnSpike()
        }
        ADebugIconBar(this, boosts + spike)
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()
        startRun()
    }

    /** Екран іде (меню, назад) — ран далі не піде, геми рану в баланс. */
    override fun hide() {
        bankRun()
        super.hide()
    }

    /**
     * Застосунок іде у фон. GDXGame зберігає ПІСЛЯ screen.pause(), тож геми,
     * забанкані тут, потраплять у сейв. Лише після смерті: живий ран після
     * resume триває, а bank() віддає суму один раз — пізніші геми загубились би.
     */
    override fun pause() {
        if (engine.phase == RunEngine.Phase.DEAD) bankRun()
        super.pause()
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
        addDebugIconBar(aDebugIconBar, aDebugPanel)   // ПІСЛЯ панелі: вона — якір
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

        // Ефекти й написи — діти поля: позиція рахується в його ж координатах.
        // Спершу спалах, потім написи: напис має лишатись поверх крапок.
        for (w in aWaves)  { w.setSize(AWave.QUAD, AWave.QUAD); aOrbitField.addActor(w) }
        for (b in aBursts) { b.setSize(BURST_SIZE, BURST_SIZE); aOrbitField.addActor(b) }
        for (f in aFloats) aOrbitField.addActor(f)
    }

    // ------------------------------------------------------------------------
    // Run
    // ------------------------------------------------------------------------

    /**
     * Новий ран. Config збирається ТУТ — рушій не читає сейв, усе, що на нього
     * впливає, передається явно. Тому ран відтворюваний: Config + seed + тапи.
     */
    private fun startRun() {
        bankRun()                 // геми попереднього рану — до того, як рушій зміниться
        releaseAllActors()

        engine = RunEngine(RunEngine.Config(
            orbit3 = debugOrbit3,
            mercy  = quickDeaths >= 2,
            // TODO: апгрейди й startBoost — коли розширимо PlayerData
        ))
        if (quickDeaths >= 2) quickDeaths = 0
        comboCommitted = 0

        engine.listener = runListener
        applyDebugFlags()
        aPanelGameHud.reset()     // ← НОВЕ

        // Кільця стартують у позиції рушія без лерпу — інакше перший кадр
        // показав би стару розкладку і смикнув би її на місце.
        syncField()
        gdxGame.analytics.runStart()
    }

    /**
     * Геми рану → баланс гравця. Рушій віддає суму рівно раз, тож зайвий
     * виклик (рестарт, потім вихід) нічого не подвоїть.
     */
    private fun bankRun() {
        val gems = engine.bank()
        if (gems <= 0) return
        gdxGame.modelPlayer.addGems(gems, AnalyticsManager.GemSource.RUN)
        gdxGame.saveGame()
        gdxGame.activity.submitScores(gdxGame.modelPlayer.leaderboardScores())   // баланс виріс — лідерборд багатства
    }

    /**
     * Перемикачі дебаг-панелі живуть в екрані, а не в рушії: новий ран — новий
     * RunEngine, і без цього кнопка лишалась би «ON», а рушій — у дефолтах.
     */
    private fun applyDebugFlags() {
        engine.debugFrozen  = debugPaused
        engine.debugEzCombo = debugEzCombo
        if (debugOrbit3) engine.debugSetOrbit3(true)
    }

    private val runListener = object : RunEngine.Listener {
        override fun onDied(result: RunEngine.RunResult) {
            quickDeaths = if (result.durationSec < 15) quickDeaths + 1 else 0

            // Рекорд і лічильник ранів — одразу. Геми — пізніше, у bankRun():
            // до рестарту чи виходу сума ще може змінитись (x2·AD, ревайв).
            val player  = gdxGame.modelPlayer
            // Комбо = спіймані іскри (RunResult.nearMisses). Зміниться механіка — міняється лише джерело тут
            val combos   = result.nearMisses
            val comboNew = combos - comboCommitted
            comboCommitted = combos

            val newBest = player.commitRun(result.score, comboNew)
            gdxGame.saveGame()
            gdxGame.activity.submitScores(player.leaderboardScores())

            gdxGame.analytics.runEnd(
                score       = result.score,
                durationSec = result.durationSec,
                gemsEarned  = result.gems,
                deathRing   = result.deathRing,
                newBest     = newBest,
            )

            // TODO: GameOver-панель (score, +gems, REVIVE·AD, X2·AD, RESTART, MENU)
            log("DEAD score=${result.score} gems=${result.gems} t=${result.durationSec}s ring=${result.deathRing} best=$newBest")
        }

        override fun onOrbit3Online() { log("ORBIT III ONLINE") }
        override fun onNearMiss(e: RunEngine.Entity, sparkAngle: Float, bonus: Int) {
            // Усе — в точці ІСКРИ, не шипа: саме за цим рушій і віддає кут
            val r = e.rr * RunEngine.TO_FIELD
            showSparkFx(r, -sparkAngle)
            showFloat("COMBO x${engine.multiplier}", r, -sparkAngle)
            log("COMBO! +$bonus")
        }
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
            ringR     = engine.ringR,
            count     = engine.ringCount,
            active    = engine.ringIndex,
            r3Alpha   = engine.ring3Alpha,
            ballAngle = -engine.angle,   // той самий переклад Y-вниз → Y-вгору, що й у syncPlayer
            ballD     = engine.radius,   // радіус рушія = діаметр поля (TO_FIELD = 0.5)
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
        aBall.heading = -engine.angle    // мінус — той самий переклад Y-вниз → Y-вгору, що й для позиції
        aBall.isVisible = true

        // Стани м'яча з рушія: щит, вікно комбо, супутників — множник − 1
        // (x2 → 1 … x5 → 4; без комбо множник 1 → нуль — кільця немає)
        aBall.shieldOn = engine.shield > 0
        aBall.setCombo(engine.comboFrac, engine.multiplier - 1)

        // Невразливість після щита — блимання
        aBall.color.a = if (engine.invuln > 0f && (engine.invuln * 10f).toInt() % 2 == 0) 0.35f else 1f
    }

    private fun syncEntities() {
        seenIds.clear()

        seenSparkIds.clear()

        for (e in engine.entities) {
            seenIds.add(e.id)
            val actor = activeActors.getOrPut(e.id) { acquire(e) }

            aOrbitField.positionAt(actor, e.rr * RunEngine.TO_FIELD, -e.a)
            actor.color.a = e.s   // spawn-fade 0→1

            // Іскра шипа: той самий переклад кута й радіуса, що й для сутностей
            val sparkA = engine.sparkAngle(e) ?: continue
            seenSparkIds.add(e.id)
            val spark = activeSparks.getOrPut(e.id) { acquireSpark(e) }

            aOrbitField.positionAt(spark, e.rr * RunEngine.TO_FIELD, -sparkA)
            spark.color.a = e.s
        }

        releaseMissing()
        releaseMissingSparks()
    }

    /**
     * Спливний напис у точці події: підіймається й лінійно тане, як pop() у
     * прототипі. Немає вільної мітки — подія просто лишається без напису:
     * пропустити напис дешевше, ніж обірвати чужий на півдорозі.
     */
    private fun showFloat(text: CharSequence, radiusDesign: Float, angleDeg: Float) {
        val lbl = aFloats.firstOrNull { !it.isVisible } ?: return

        lbl.setText(text)
        lbl.color.a   = 1f
        lbl.isVisible = true
        aOrbitField.positionAt(lbl, radiusDesign, angleDeg)
        lbl.toFront()

        lbl.addAction(Actions.sequence(
            Actions.parallel(
                Actions.moveBy(0f, FLOAT_RISE, FLOAT_LIFE),
                Actions.fadeOut(FLOAT_LIFE),
            ),
            Actions.visible(false),
        ))
    }

    /**
     * Спалах на місці спійманої іскри: кільцева хвиля + розліт крапок. Світ у
     * цю мить стоїть (RunEngine.HIT_STOP_NEAR), тож перші два кадри ефект
     * розходиться із застиглої картинки — це й читається як удар.
     *
     * Немає вільного актора — ефект просто пропускаємо, як і напис: обірвати
     * чужий спалах на півдорозі гірше, ніж не показати цей.
     */
    private fun showSparkFx(radiusDesign: Float, angleDeg: Float) {
        aWaves.firstOrNull { !it.isVisible }?.let {
            aOrbitField.positionAt(it, radiusDesign, angleDeg)
            it.fire()
        }
        aBursts.firstOrNull { !it.isVisible }?.let {
            aOrbitField.positionAt(it, radiusDesign, angleDeg)
            it.fire()
        }
    }

    /** HUD читає рушій сам: рахунок, геми рану, комбо, щит. */
    private fun syncHud() {
        aPanelGameHud.syncFrom(engine)
    }

    // ------------------------------------------------------------------------
    // Pool
    // ------------------------------------------------------------------------
    private fun acquire(e: RunEngine.Entity): Actor = when (e.kind) {
        RunEngine.Kind.GEM   -> freeGems.removeLastOrNull()    ?: AGem(this).also { prepare(it, GEM_SIZE) }
        RunEngine.Kind.SPIKE -> freeSpikes.removeLastOrNull()  ?: ASpike(this).also { prepare(it, SPIKE_SIZE) }
        RunEngine.Kind.BOOST -> (freeBoosts.removeLastOrNull() ?: ABooster(this).also { prepare(it, BOOST_SIZE_W, BOOST_SIZE_H) })
            .also { it.boost = e.boost ?: RunEngine.Boost.MAGNET }
    }.also { it.isVisible = true }

    private fun prepare(actor: Actor, width: Float, height: Float = width) {
        actor.setSize(width, height)
        aOrbitField.addActor(actor)
    }

    /** Фаза пульсу — з кута шипа, як у прототипі; toFront — над шипом, доданим пізніше. */
    private fun acquireSpark(e: RunEngine.Entity): ASpark =
        (freeSparks.removeLastOrNull() ?: ASpark(this).also { prepare(it, SPARK_SIZE) }).also {
            it.phase     = e.a * MathUtils.degreesToRadians
            it.isVisible = true
            it.toFront()
        }

    /** Іскру спіймано або шип зник — актор іскри в пул. */
    private fun releaseMissingSparks() {
        val it = activeSparks.entries.iterator()
        while (it.hasNext()) {
            val (id, spark) = it.next()
            if (id in seenSparkIds) continue

            spark.isVisible = false
            freeSparks.add(spark)
            it.remove()
        }
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
        // Написи й спалахи минулого рану дограли б поверх нового поля
        for (f in aFloats) { f.clearActions(); f.isVisible = false }
        for (w in aWaves)  w.isVisible = false
        for (b in aBursts) b.isVisible = false

        seenIds.clear()
        seenSparkIds.clear()
        releaseMissing()
        releaseMissingSparks()
    }

}