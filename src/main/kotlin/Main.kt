package tech.lq0.modSyncNext

import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml

fun main() {
    Toml.encodeToString(
        getConfig()
    ).also(::println)
}