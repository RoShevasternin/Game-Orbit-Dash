package com.lewydo.orbitdash.game.manager

import com.badlogic.gdx.Gdx
import com.lewydo.orbitdash.game.GDXGame
import com.lewydo.orbitdash.game.screens.BrandScreen
import com.lewydo.orbitdash.game.screens.GameScreen
import com.lewydo.orbitdash.game.screens.LeaderboardScreen
import com.lewydo.orbitdash.game.screens.LoaderScreen
import com.lewydo.orbitdash.game.screens.MenuScreen
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.runGDX

class NavigationManager(val game: GDXGame) {

    private val backStack = mutableListOf<String>()
    var key: Int? = null
        private set

    // ------------------------------------------------------------------------
    //  ЗВІДКИ ПРИЙШЛИ.
    //
    //  AdvancedGame.screen приватний, тож попередній екран напряму не дістати.
    //  Але NavigationManager — єдина точка, через яку відбуваються ВСІ переходи,
    //  тож достатньо самому вести пару «поточний / попередній».
    //
    //  Кому це потрібно: MenuScreen мусить знати, чи приїхав він з лоадера.
    //  З лоадера aMain уже стоїть у кадрі на тих самих якорях — це безшовний
    //  морф, і анімація появи його зламала б. З GameScreen чи RANKS брендблоку
    //  не існувало, і він має з'явитись сам.
    // ------------------------------------------------------------------------

    /**
     * Ім'я екрана, з якого щойно прийшли. null — перший екран застосунку.
     * Читати в show()/animShowScreen нового екрана: на момент показу тут
     * уже лежить попередник.
     */
    var fromScreenName: String? = null
        private set

    private var currentScreenName: String? = null

    /**
     * УВАГА, ДВА РІЗНИХ fromScreenName:
     *   • параметр — ЯВНА вказівка, що покласти в бекстек; часто не передається;
     *   • поле this.fromScreenName — ФАКТИЧНИЙ попередник, ведеться завжди.
     * Тому нижче звертання через this.
     */
    fun navigate(toScreenName: String, fromScreenName: String? = null, key: Int? = null) = runGDX {
        this.key = key

        this.fromScreenName = currentScreenName
        currentScreenName   = toScreenName

        game.updateScreen(getScreenByName(toScreenName))
        backStack.filter { name -> name == toScreenName }.onEach { name -> backStack.remove(name) }
        fromScreenName?.let { fromName ->
            backStack.filter { name -> name == fromName }.onEach { name -> backStack.remove(name) }
            backStack.add(fromName)
        }
    }

    fun back(key: Int? = null) = runGDX {
        this.key = key

        if (isBackStackEmpty()) {
            exit()
        } else {
            // Ціль дістаємо ДО updateScreen — інакше нічого не запишемо в пару.
            val target = backStack.removeAt(backStack.lastIndex)

            this.fromScreenName = currentScreenName
            currentScreenName   = target

            game.updateScreen(getScreenByName(target))
        }
    }


    fun exit() = runGDX { Gdx.app.exit() }


    fun isBackStackEmpty() = backStack.isEmpty()

    private fun getScreenByName(name: String): AdvancedScreen = when(name) {
        BrandScreen      ::class.java.name -> BrandScreen()
        LoaderScreen     ::class.java.name -> LoaderScreen()
        MenuScreen       ::class.java.name -> MenuScreen()
        GameScreen       ::class.java.name -> GameScreen()
        LeaderboardScreen::class.java.name -> LeaderboardScreen()

        else -> MenuScreen()
    }

}
