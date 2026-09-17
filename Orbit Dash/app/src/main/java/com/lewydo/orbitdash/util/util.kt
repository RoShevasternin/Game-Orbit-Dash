package com.lewydo.orbitdash.util

import android.util.Log
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

val Any.currentClassName: String get() = this::class.java.simpleName

fun log(message: String) {
    if (IS_DEBUG) Log.i("OD_DEBUG", message)
}

fun cancelCoroutinesAll(vararg coroutine: CoroutineScope?) {
    coroutine.forEach { it?.cancel() }
}