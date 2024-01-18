package tech.lq0.modSyncNext

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlComment
import java.io.File

@Serializable
data class Config(
    @TomlComment("配置文件版本，勿动")
    val version: String,
    val sync: SyncConfig,
    val minecraft: MinecraftConfig,
)

@Serializable
data class SyncConfig(
    @TomlComment("同步服务器")
    val server: String,
    @TomlComment("是否自动更新同步程序")
    val autoUpdate: Boolean = false,
    @TomlComment("是否在程序启动后自动开始同步")
    val autoSync: Boolean = false,
    @TomlComment("同步完成后的行为")
    val actionAfterSync: Int = 0,
    @TomlComment("若行为为执行命令，则执行下述命令")
    val command: String = "",
)

@Serializable
data class MinecraftConfig(
    @TomlComment("要同步的Minecraft版本")
    val version: String,
    @TomlComment("是否开启版本隔离")
    val isolate: Boolean = true,
    @TomlComment("是否同步配置文件（仅在配置文件不存在时同步）")
    val syncConfig: Boolean = true,
)

@Serializable
data class ConfigLegacy(
    val version: String,
    val serverName: String,
    val autoSync: Boolean,
    val actionAfterSync: Int,
    val command: String,
    val autoUpdate: Boolean
)

fun requireString(message: String, condition: ((String) -> Boolean)?): String {
    while (true) {
        print(message)
        val temp = readln()
        if (temp.isNotBlank() && condition?.invoke(temp) != false) return temp
    }
}

fun getConfig(): Config {
    if (File("msnconfig.txt").exists()) return Toml.decodeFromString(File("msnconfig.txt").readText())

    var syncServer: String =
        requireString("请输入同步服务器（不是Minecraft版本）：") { it.startsWith("http://") || it.startsWith("https://") }
    if (!syncServer.endsWith("/")) syncServer += "/"

    val minecraftVersion = requireString("请输入要同步的Minecraft版本：") { it.isNotBlank() }

    if (File("mcsyncconfig-1.0.json").exists()) {
        val oldConfig: ConfigLegacy = Json.decodeFromString(File("mcsyncconfig-1.0.json").readText())
        val newConfig = Config(
            version = "2.0",
            sync = SyncConfig(
                server = syncServer,
                autoSync = oldConfig.autoSync,
                actionAfterSync = oldConfig.actionAfterSync,
                command = oldConfig.command,
                autoUpdate = oldConfig.autoUpdate
            ),
            minecraft = MinecraftConfig(
                version = minecraftVersion,
                isolate = true,
                syncConfig = true
            )
        )
        File("msnconfig.txt").writeText(Toml.encodeToString(newConfig))
        return newConfig
    }

    val defaultConfig = Config(
        version = "2.0",
        sync = SyncConfig(
            server = syncServer,
            autoSync = false,
            actionAfterSync = 0,
            command = "",
            autoUpdate = false
        ),
        minecraft = MinecraftConfig(
            version = minecraftVersion,
            isolate = true,
            syncConfig = true
        )
    )
    File("msnconfig.txt").writeText(Toml.encodeToString(defaultConfig))
    return defaultConfig
}