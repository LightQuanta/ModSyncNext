package tech.lq0.modSyncNext

import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import java.io.File
import kotlin.system.exitProcess


fun main() = runBlocking {
    AnsiConsole.systemInstall()
    if (!File(".minecraft").exists() || !File(".minecraft").isDirectory) {
        println(ansi().fgRed().a("请将本程序放置于.minecraft同级目录下！").reset())
        println("按回车退出")
        readln()
        exitProcess(0)
    }

    // TODO 修改为正确读取配置文件并执行对应操作
    val version = globalConfig.minecraft.version

    println(ansi().fgCyan().a("ModSyncNext").fgRgb(128, 128, 128).a(" by ").fgBrightCyan().a("Light_Quanta").reset())
    syncMod(version)

    AnsiConsole.systemUninstall()
}