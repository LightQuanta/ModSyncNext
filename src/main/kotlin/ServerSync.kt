package tech.lq0.modSyncNext

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private val command = globalConfig.sync.command
fun importVersion() {
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

fun serverModSync() {
    val versionList =
        File("./mods").listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    if (versionList.isEmpty()) {
        println("目前还没有服务器！\n".red())
        return
    }

    val version = requireString("请输入要同步的服务器名称") { n -> n in versionList }
    println("正在同步 $version ...".cyan())

    // 读取旧mod列表信息
    val csvFile = File("./MSN/server/filelist-$version.csv")
    val oldModsInfo = runCatching {
        csvFile.readText().split("\n").map { it.split(",").reversed() }.associate { it[0] to it[1] }
    }.getOrDefault(mapOf())

    // 写入新mod列表信息
    readFileHashCache()
    val newModsInfo = computeAllHashForFolder("./mods/$version")
    csvFile.writeText(newModsInfo.map { (k, v) -> "$v,$k" }.joinToString("\n"))
    writeFileHashCache()

    // 将要上传的新mod复制到临时文件夹
    val modsToUpload = newModsInfo.keys - oldModsInfo.keys
    val tempFolder = File("./temp/").also { it.mkdirs() }

    for (hash in modsToUpload) {
        val mod = newModsInfo[hash]
        val modFile = File("./mods/$version/$mod")
        val tempModFile = File("./temp/$mod")
        modFile.copyTo(tempModFile, true)
    }

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

    // 记得清空新mod
    tempFolder.deleteRecursively()
}