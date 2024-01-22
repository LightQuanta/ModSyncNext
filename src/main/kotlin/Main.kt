package tech.lq0.modSyncNext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.fusesource.jansi.AnsiConsole
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

val version = globalConfig.minecraft.version

fun main(args: Array<String>) = runBlocking {
    AnsiConsole.systemInstall()

    if (args.isNotEmpty() && args[0] == "--server") {
        val command = globalConfig.sync.command
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
                1 -> {
                    val zips = File("./mods").listFiles { _, name -> name.matches(Regex("""^sync-\w+\.zip""")) }
                    if (zips != null) {
                        for (zip in zips) {
                            val name = zip.name.drop(5).dropLast(4)
                            println("正在导入 $name".cyan())
                            val tempDir = File("./mods/temp-$name")
                            tempDir.mkdirs()
                            unzip(File(zip.absolutePath), tempDir)

                            val modsFolder = File("./mods/$name")
                            modsFolder.deleteRecursively()

                            copyFolder("./mods/temp-$name/mods", "./mods/$name")
                            val versionInfo = File("./mods/temp-$name/version-info.json").readText()
                            val versionFile = File("./MSN/server/version-$name.json")
                            versionFile.writeText(versionInfo)

                            val newFile = File("./MSN/server/config-$name.zip")
                            newFile.writeBytes(File("./mods/temp-$name/config.zip").readBytes())

                            tempDir.deleteRecursively()
                            zip.delete()

                            println("导入 $name 成功！\n".green())
                        }
                    } else {
                        println("未发现新版本！".red())
                    }
                }

                2 -> {
                    val versionList =
                        File("./mods").listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                    if (versionList.isEmpty()) {
                        println("目前还没有服务器！\n".red())
                        continue
                    }

                    val version = requireString("请输入要同步的服务器名称") { n -> n in versionList }
                    println("正在同步 $version ...".cyan())

                    readFileHashCache()
                    val hash = computeAllHashForFolder("./mods/$version")
                    val csv = hash.map { (k, v) -> "$v,$k" }.joinToString("\n")
                    File("./MSN/server/filelist-$version.csv").writeText(csv)
                    writeFileHashCache()

                    val process =
                        Runtime.getRuntime().exec(command.replace("{version}", version))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))

                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        println(line)
                    }

                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        println()
                        println("同步成功！".green())
                    } else {
                        println()
                        println("同步失败，请重试或检查配置文件".red())
                    }
                }

                3 -> exitProcess(0)
            }
        }
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