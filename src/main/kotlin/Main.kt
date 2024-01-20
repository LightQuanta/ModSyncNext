package tech.lq0.modSyncNext

import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.AnsiConsole
import java.io.File


fun main() = runBlocking {
    AnsiConsole.systemInstall()
    if (!File(".minecraft").exists() || !File(".minecraft").isDirectory) {
        exitWithHint("请将本程序放置于.minecraft同级目录下！".red())
    }

    // TODO 修改为正确读取配置文件并执行对应操作
    val version = globalConfig.minecraft.version

    println("ModSyncNext".cyan() + " by ".brightBlack() + "Light_Quanta".cyan())
    println()

    syncMod(version)

    AnsiConsole.systemUninstall()
}