package tech.lq0.modSyncNext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.fusesource.jansi.AnsiConsole
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

val version = globalConfig.minecraft.version

fun main() = runBlocking {
    AnsiConsole.systemInstall()
    if (!File(".minecraft").exists() || !File(".minecraft").isDirectory) {
        exitWithHint("请将本程序放置于.minecraft同级目录下！".red())
    }

    var updateChecked = false
    var synced = false

    while (true) {
        println("ModSyncNext".cyan() + " by ".brightBlack() + "Light_Quanta".cyan())
        println("同步版本：" + version.yellow())
        println()

        if (globalConfig.sync.autoUpdate && !updateChecked) {
            println("正在检查更新".cyan())

            // TODO 自动更新

            println("当前版本已是最新！".cyan())
            updateChecked = true
            println()
        }

        // 自动同步
        if (globalConfig.sync.autoSync && !synced) {
            sync()
        } else {
            println(
                """
                [菜单]
                
                1. 开始自动同步
                2. 修改要同步的Minecraft版本
                3. 修改配置文件
                4. 生成同步文件
                5. 退出程序
                
                """.trimIndent().green()
            )
            val operation =
                requireStringOrDefault("请选择你的操作（默认为1）", "1") { (it.toIntOrNull() ?: false) in 1..5 }.toInt()
            when (operation) {
                1 -> sync()
                2 -> interactiveSetSyncVersion()
                3 -> interactiveSetConfig()
                4 -> generateSyncInfo()
                5 -> {
                    println("程序将在5s后自动退出".yellow())
                    delay(5.seconds)
                    AnsiConsole.systemUninstall()
                    exitProcess(0)
                }
            }
        }
        synced = true
    }
}

suspend fun sync() {
    println(">>> 开始同步mod".cyan())
    println()
    syncMod(version)

    println(">>> 同步完成".cyan())
    println()

    if (globalConfig.sync.actionAfterSync == ActionAfterSync.Exit) {
        println("程序将在5s后自动退出".yellow())
        delay(5.seconds)
        exitProcess(0)
    }

    if (globalConfig.sync.actionAfterSync == ActionAfterSync.DoNothing) {
        return
    }

    if (globalConfig.sync.actionAfterSync == ActionAfterSync.ExecuteCommand || globalConfig.sync.actionAfterSync == ActionAfterSync.ExecuteCommandAndExit) {
        println(">>> 开始执行外部程序".cyan())

        val command = globalConfig.sync.command
        try {
            withContext(Dispatchers.IO) {
                Runtime.getRuntime().exec(command)
            }
            println(">>> 执行成功！\n".green())
        } catch (e: Exception) {
            println(">>> 执行失败，请检查你的配置文件\n$e\n".red())
        }
        if (globalConfig.sync.actionAfterSync == ActionAfterSync.ExecuteCommandAndExit) {
            println("程序将在5s后自动退出".yellow())
            delay(5.seconds)
            AnsiConsole.systemUninstall()
            exitProcess(0)
        }
    }
}