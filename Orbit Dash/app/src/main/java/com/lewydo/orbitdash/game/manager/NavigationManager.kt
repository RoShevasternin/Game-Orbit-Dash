package com.lewydo.orbitdash.game.manager

import com.badlogic.gdx.Gdx
import com.lewydo.orbitdash.game.GDXGame
import com.lewydo.orbitdash.game.screens.BrandScreen
import com.lewydo.orbitdash.game.screens.LeaderboardScreen
import com.lewydo.orbitdash.game.screens.LoaderScreen
import com.lewydo.orbitdash.game.screens.MenuScreen
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.runGDX

class NavigationManager(val game: GDXGame) {

    private val backStack = mutableListOf<String>()
    var key: Int? = null
        private set

    fun navigate(toScreenName: String, fromScreenName: String? = null, key: Int? = null) = runGDX {
        this.key = key

        game.updateScreen(getScreenByName(toScreenName))
        backStack.filter { name -> name == toScreenName }.onEach { name -> backStack.remove(name) }
        fromScreenName?.let { fromName ->
            backStack.filter { name -> name == fromName }.onEach { name -> backStack.remove(name) }
            backStack.add(fromName)
        }
    }

    fun back(key: Int? = null) = runGDX {
        this.key = key

        if (isBackStackEmpty()) exit() else game.updateScreen(getScreenByName(backStack.removeAt(backStack.lastIndex)))
    }


    fun exit() = runGDX { Gdx.app.exit() }


    fun isBackStackEmpty() = backStack.isEmpty()

    private fun getScreenByName(name: String): AdvancedScreen = when(name) {
        BrandScreen      ::class.java.name -> BrandScreen()
        LoaderScreen     ::class.java.name -> LoaderScreen()
        MenuScreen       ::class.java.name -> MenuScreen()
        LeaderboardScreen::class.java.name -> LeaderboardScreen()

        else -> MenuScreen()//GameScreen()
    }

}