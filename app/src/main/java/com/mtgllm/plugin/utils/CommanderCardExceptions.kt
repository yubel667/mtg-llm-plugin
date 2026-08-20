package com.mtgllm.plugin.utils

import android.content.Context
import java.io.InputStream

object CommanderCardExceptions {
    private const val ASSET_NAME = "commander_card_exceptions.txt"

    @Volatile
    private var cachedNames: Set<String>? = null

    fun load(context: Context): Set<String> {
        return cachedNames ?: synchronized(this) {
            cachedNames ?: parse(context.assets.open(ASSET_NAME)).also { cachedNames = it }
        }
    }

    internal fun parse(input: InputStream): Set<String> {
        val names = input.bufferedReader().useLines { lines ->
            lines
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }

        require(names.isNotEmpty()) { "$ASSET_NAME must contain at least one card name." }
        require(names.all { it.contains("commander", ignoreCase = true) }) {
            "$ASSET_NAME may only contain card names with 'commander'."
        }
        require(names.size == names.map { it.lowercase() }.toSet().size) {
            "$ASSET_NAME may not contain duplicate card names."
        }

        return names
    }
}
