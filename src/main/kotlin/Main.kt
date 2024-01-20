package tech.lq0.modSyncNext

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.AnsiConsole
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds


fun main() = runBlocking {
    AnsiConsole.systemInstall()
    if (!File(".minecraft").exists() || !File(".minecraft").isDirectory) {
        exitWithHint("请将本程序放置于.minecraft同级目录下！".red())
    }

    // TODO 修改为正确读取配置文件并执行对应操作
    val version = globalConfig.minecraft.version

    println("ModSyncNext".cyan() + " by ".brightBlack() + "Light_Quanta".cyan())
    println()

    println("开始同步mod".cyan())
    println()
    syncMod(version)

    println("同步完成".cyan())
    println()

    if (globalConfig.sync.actionAfterSync == ActionAfterSync.Exit) {
        println("程序将在5s后自动退出".yellow())
        delay(5.seconds)
        exitProcess(0)
    }

    if (globalConfig.sync.actionAfterSync == ActionAfterSync.DoNothing) {
        // TODO 返回菜单
    }

    if (globalConfig.sync.actionAfterSync == ActionAfterSync.ExecuteCommand || globalConfig.sync.actionAfterSync == ActionAfterSync.ExecuteCommandAndExit) {
        println("开始执行外部程序".cyan())
        println()

        val command = globalConfig.sync.command
        try {
            Runtime.getRuntime().exec(command)
            println("执行成功".green())
        } catch (e: Exception) {
            println("执行失败，请检查你的配置文件\n$e".red())
        }
        if (globalConfig.sync.actionAfterSync == ActionAfterSync.ExecuteCommandAndExit) {
            println("程序将在5s后自动退出".yellow())
            delay(5.seconds)
            exitProcess(0)
        }

        // TODO 返回菜单
    }

    AnsiConsole.systemUninstall()
}