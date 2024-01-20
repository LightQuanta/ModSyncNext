package tech.lq0.modSyncNext

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

const val versionDir = ".minecraft/versions"

@Serializable
data class MinecraftVersionInfo(
    val name: String,
    val version: String,
    val modLoader: ModLoaderInfo,
)

@Serializable
data class ModLoaderInfo(
    val type: ModLoaderType,
    val version: String,
)

@Serializable
enum class ModLoaderType {
    Forge,
    Fabric,
    Vanilla,
}

fun getVersionInfo(name: String): MinecraftVersionInfo? {
    if (!File("$versionDir/$name/$name.json").exists()) return null
    val versionInfo = Json.parseToJsonElement(File("$versionDir/$name/$name.json").readText())
    val args = versionInfo.jsonObject["arguments"]?.jsonObject?.get("game")?.jsonArray ?: return null
    val strArgs = args.map { it.toString() }

    // Forge
    if (strArgs.any { "--fml.forgeVersion" in it }) {
        val forgeVersion = strArgs[strArgs.indexOf("\"--fml.forgeVersion\"") + 1].trim('"')
        val minecraftVersion = strArgs[strArgs.indexOf("\"--fml.mcVersion\"") + 1].trim('"')
        return MinecraftVersionInfo(
            name,
            minecraftVersion,
            ModLoaderInfo(
                ModLoaderType.Forge,
                forgeVersion
            )
        )
    }

    val mcVersion = (versionInfo.jsonObject["gameVersion"]
        ?: versionInfo.jsonObject["clientVersion"])?.jsonPrimitive?.content ?: return null

    // Fabric
    val libraries = versionInfo.jsonObject["libraries"]?.jsonArray
    if (libraries?.any { "net.fabricmc:fabric-loader:" in it.jsonObject["name"].toString() } == true) {
        val fabricVersion = libraries.first { "net.fabricmc:fabric-loader:" in it.jsonObject["name"].toString() }
            .jsonObject["name"]
            .toString()
            .trim('\"')
            .takeLastWhile { it != ':' }
        return MinecraftVersionInfo(
            name,
            mcVersion,
            ModLoaderInfo(
                ModLoaderType.Fabric,
                fabricVersion
            )
        )
    }
    return MinecraftVersionInfo(
        name,
        mcVersion,
        ModLoaderInfo(
            ModLoaderType.Vanilla,
            mcVersion
        )
    )
}

fun getVersionList(): List<MinecraftVersionInfo> {
    val versions = File(versionDir).listFiles()
        ?.filter { it.isDirectory }
        ?.map { it.name }
    return versions?.mapNotNull(::getVersionInfo) ?: listOf()
}