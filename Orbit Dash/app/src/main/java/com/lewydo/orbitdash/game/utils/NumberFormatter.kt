package com.lewydo.orbitdash.game.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberFormatter {

    private val formatter = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US)).apply {
        isGroupingUsed = true
    }

    fun format(value: Long): String {
        return formatter.format(value)
    }

    fun format(value: Int): String {
        return formatter.format(value)
    }
}