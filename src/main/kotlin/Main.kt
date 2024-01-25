package tech.lq0.modSyncNext

import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.fusesource.jansi.AnsiConsole
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

val version: String
    get() = globalConfig.minecraft.version

fun main(args: Array<String>) = runBlocking {
    AnsiConsole.systemInstall()

    try {
        initConfig()
    } catch (e: Exception) {
        println("读取配置文件出错：$e".red())
        println("请关闭程序自行编辑msnconfig.txt，或按回车重新设置配置文件".yellow())
        readln()
        requireString("警告：重新设置配置文件将会覆盖原有配置，确定要继续吗？\n输入YES继续".yellow()) { it == "YES" }
        setAndSaveConfig()
    }

    if (args.isNotEmpty() && args[0] == "--server") {
        println("正在以服务端模式运行\n".cyan())

        File("./MSN/server").mkdirs()
        while (true) {
            println(
                """
                [菜单]
                
                1. 导入整合包信息
                2. 同步服务器
                3. 退出
                
                """.trimIndent().green()
            )
            val operation =
                requireStringOrDefault("请选择你的操作（默认为2）", "2") { (it.toIntOrNull() ?: false) in 1..3 }.toInt()

            when (operation) {
                1 -> importVersion()
                2 -> serverModSync()
                3 -> {
                    println("Bye".cyan())
                    exitProcess(0)
                }
            }
        }
    }

    var synced = false

    println("ModSyncNext".cyan() + " by ".brightBlack() + "Light_Quanta".cyan())
    println("同步版本：" + version.yellow())
    println()

    if (globalConfig.sync.autoUpdate) {
        autoUpdate()
    }


    while (true) {
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
                4. 检查更新
                5. 生成同步文件
                6. 退出程序
                
                """.trimIndent().green()
            )
            val operation =
                requireStringOrDefault("请选择你的操作（默认为1）", "1") { (it.toIntOrNull() ?: false) in 1..5 }.toInt()
            when (operation) {
                1 -> sync()
                2 -> interactiveSetSyncVersion()
                3 -> setAndSaveConfig()
                4 -> autoUpdate()
                5 -> generateSyncInfo()
                6 -> {
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

suspend fun autoUpdate() {
    val classLoader = Thread.currentThread().contextClassLoader
    val resourceAsStream = classLoader.getResourceAsStream("version")!!
    val currentVersion = resourceAsStream.bufferedReader().use { it.readText() }

    println("正在检查更新，当前版本：$currentVersion".cyan())

    val server = globalConfig.sync.server
    "$server/msnversion.txt".httpGet().awaitStringResponseResult().third.fold(
        { data -> data },
        { error ->
            println("获取版本信息出错：$error".red())
            null
        }
    )?.let { newVersion ->
        if (newVersion != currentVersion) {
            println("发现新版本 $newVersion ，正在自动更新".cyan())
//            val path = object {}.javaClass.protectionDomain.codeSource.location.toURI().path

            val tempFile = File("ModSyncNext-$newVersion.jar")

            "$server/ModSyncNext-$newVersion.jar".httpGet().awaitByteArrayResponseResult().third.fold(
                { data -> data },
                { error ->
                    println("获取版本信息出错：$error".red())
                    null
                }
            )?.let { data ->
                tempFile.writeBytes(data)
                exitWithHint("下载新版本成功！请手动将".cyan() + " ModSyncNext-$newVersion.jar ".yellow() + "重命名为".cyan() + " ModSyncNext.jar ".yellow() + "，再重启本程序".cyan())
            }
        } else {
            println("当前版本 $currentVersion 已是最新！".cyan())
        }
    }
    println()
}


suspend fun sync() {
    if (version.isEmpty()) interactiveSetSyncVersion()

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