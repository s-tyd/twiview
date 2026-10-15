package dev.twiview.ui.home

import androidx.annotation.StringRes
import dev.twiview.R

const val ALL_STREAMS = "all"

private val byAudience = listOf("en", "pt", "de", "fr", "ru", "es", "it", "tr", "ko", "ja")

fun streamFilterOrder(saved: List<String>, mine: String?): List<String> {
    val known = byAudience + ALL_STREAMS
    val base = saved.filter { it in known }.distinct().ifEmpty { listOfNotNull(mine) + ALL_STREAMS + byAudience.filter { it != mine } }
    return base + known.filter { it !in base }
}

@StringRes
fun streamFilterLabel(tag: String): Int = when (tag) {
    "ja" -> R.string.region_ja
    "ko" -> R.string.region_ko
    "pt" -> R.string.region_pt
    "de" -> R.string.region_de
    "fr" -> R.string.region_fr
    "ru" -> R.string.region_ru
    "es" -> R.string.region_es
    "it" -> R.string.region_it
    "tr" -> R.string.region_tr
    "en" -> R.string.region_en
    else -> R.string.all
}
