package tech.lq0.modSyncNext

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlComment
import java.io.File

var globalConfig = Config(
    version = "2.0",
    sync = SyncConfig(),
    minecraft = MinecraftConfig()
)

@Serializable
data class Config(
    @TomlComment("配置文件版本，勿动")
    val version: String,
    var sync: SyncConfig,
    var minecraft: MinecraftConfig,
)

@Serializable
data class SyncConfig(
    @TomlComment("同步服务器")
    var server: String = "",
    @TomlComment("是否自动更新同步程序")
    var autoUpdate: Boolean = false,
    @TomlComment("是否在程序启动后自动开始同步")
    var autoSync: Boolean = false,
    @TomlComment(
        """
        同步完成后的行为，可用的值为：
        Exit ：同步后退出程序
        DoNothing：同步后等待用户操作
        ExecuteCommand：同步后执行命令
        ExecuteCommandAndExit：同步后执行命令并退出
        """
    )
    var actionAfterSync: ActionAfterSync = ActionAfterSync.DoNothing,
    @TomlComment("若行为为执行命令，则执行下述命令")
    var command: String = "",
)

enum class ActionAfterSync {
    Exit,
    DoNothing,
    ExecuteCommand,
    ExecuteCommandAndExit
}

@Serializable
data class MinecraftConfig(
    @TomlComment("要同步的Minecraft版本")
    var version: String = "",
    @TomlComment("是否开启版本隔离")
    var isolate: Boolean = true,
    @TomlComment("是否同步配置文件（仅在配置文件不存在时同步）")
    var syncConfig: Boolean = true,
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

fun interactiveSetSyncVersion() {
    val version = requireStringOrDefault(
        "请输入要同步的Minecraft版本（当前为${globalConfig.minecraft.version.yellow()}）",
        globalConfig.minecraft.version
    ) { it.isNotEmpty() }
    globalConfig.minecraft.version = version

    File("msnconfig.txt").writeText(Toml.encodeToString(globalConfig))
    println("修改成功！\n".green())
}

fun setAndSaveConfig() {
    val config = interactiveSetConfig()
    globalConfig.sync = config.sync
    globalConfig.minecraft = config.minecraft
    File("msnconfig.txt").writeText(Toml.encodeToString(config))
    println("已保存配置文件！\n".green())
}

fun interactiveSetConfig(): Config {
    println("\n开始设置配置文件，请输入新配置，不输入内容则表示不更改配置".cyan())
    val server = requireStringOrDefault(
        "请输入同步服务器（当前为${globalConfig.sync.server.yellow()}）",
        globalConfig.sync.server
    ) { it.startsWith("http://") || it.startsWith("https://") }

    val autoUpdate = requireBooleanOrDefault(
        "是否自动更新同步程序（当前为${globalConfig.sync.autoUpdate.toString().yellow()}）",
        globalConfig.sync.autoUpdate
    )
    val autoSync = requireBooleanOrDefault(
        "是否在程序启动后自动开始同步（当前为${globalConfig.sync.autoSync.toString().yellow()}）",
        globalConfig.sync.autoSync
    )

    val actionAfterSync = ActionAfterSync.valueOf(
        requireStringOrDefault(
            """
            同步完成后的行为（当前为${globalConfig.sync.actionAfterSync.name.yellow()}）
            可选项为：${ActionAfterSync.entries.joinToString { it.name.brightBlack() }}
            """.trimIndent(),
            globalConfig.sync.actionAfterSync.name
        ) {
            it in ActionAfterSync.entries.map { e -> e.name }
        }
    )

    val command = requireStringOrDefault(
        "同步后要执行的命令（当前为${globalConfig.sync.command.yellow()}）",
        globalConfig.sync.command
    )

    val version = requireStringOrDefault(
        "请输入要同步的Minecraft版本（当前为${globalConfig.minecraft.version.yellow()}）",
        globalConfig.minecraft.version
    ) { it.isNotEmpty() }

    val isolate = requireBooleanOrDefault(
        "是否开启版本隔离（当前为${globalConfig.minecraft.isolate.toString().yellow()}）",
        globalConfig.minecraft.isolate
    )

    val syncConfig = requireBooleanOrDefault(
        "是否同步配置文件（当前为${globalConfig.minecraft.syncConfig.toString().yellow()}）",
        globalConfig.minecraft.syncConfig
    )

    return Config(
        version = "2.0",
        sync = SyncConfig(
            server = server,
            autoSync = autoSync,
            actionAfterSync = actionAfterSync,
            command = command,
            autoUpdate = autoUpdate
        ),
        minecraft = MinecraftConfig(
            version = version,
            isolate = isolate,
            syncConfig = syncConfig
        )
    )
}

fun initConfig() {
    if (File("msnconfig.txt").exists()) {
        val config: Config = Toml.decodeFromString(
            File("msnconfig.txt").readText()
        )
        globalConfig = config
        return
    }

    if (File("mcsyncconfig-1.0.json").exists()) {
        val syncServer: String =
            requireString("请输入同步服务器（不是Minecraft版本）") { it.startsWith("http://") || it.startsWith("https://") }
                .trim('/')
        val oldConfig: ConfigLegacy = Json.decodeFromString(File("mcsyncconfig-1.0.json").readText())
        val newConfig = Config(
            version = "2.0",
            sync = SyncConfig(
                server = syncServer,
                autoSync = oldConfig.autoSync,
                actionAfterSync = when (oldConfig.actionAfterSync) {
                    0 -> ActionAfterSync.Exit
                    2 -> ActionAfterSync.ExecuteCommand
                    3 -> ActionAfterSync.ExecuteCommandAndExit
                    else -> ActionAfterSync.DoNothing
                },
                command = oldConfig.command,
                autoUpdate = oldConfig.autoUpdate
            ),
            minecraft = MinecraftConfig(
                version = oldConfig.serverName,
                isolate = false,
                syncConfig = true
            )
        )
        File("msnconfig.txt").writeText(Toml.encodeToString(newConfig))
        println("已迁移旧版配置文件！".green())
        globalConfig = newConfig
        return
    }

    val newConfig = interactiveSetConfig()
    globalConfig = newConfig
    File("msnconfig.txt").writeText(Toml.encodeToString(globalConfig))
}