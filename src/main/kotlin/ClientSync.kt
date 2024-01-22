package tech.lq0.modSyncNext

import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URLEncoder

private val server = globalConfig.sync.server.trim('/')

private fun Map<String, String>.printModsInfo() =
    println(this.map { (k, v) -> v.yellow() + " -> " + k.blue() }.joinToString("\n").ifEmpty { "（无）" })

suspend fun ensureVersionExist(version: String) {
    if (getVersionList().any { it.name == version }) return

    println("未发现版本$version，开始安装".yellow())
    println()

    val cmclFile = File("cmcl.exe")
    if (!cmclFile.exists() || !cmclFile.isFile) {
        exitWithHint(
            """
            未发现cmcl.exe，请在目录下放置cmcl.exe再开始进行自动安装
            可在 https://gitee.com/MrShiehX/console-minecraft-launcher/releases 下载
            """.trimIndent().red()
        )
    }

    val result = "$server/version-$version.json".httpGet().awaitStringResponseResult().third

    val versionInfo: MinecraftVersionInfo = result.fold(
        { data -> Json.decodeFromString(data) },
        { error -> exitWithHint("获取版本 $version 信息出错 ：$error".red()) }
    )

    val minecraftVersion = versionInfo.version
    val modLoaderInstallArg = when (versionInfo.modLoader.type) {
        ModLoaderType.Forge -> "--forge=" + versionInfo.modLoader.version + " "
        ModLoaderType.Fabric -> "--fabric=" + versionInfo.modLoader.version + " "
        ModLoaderType.Vanilla -> ""
    }

    withContext(Dispatchers.IO) {
        // 仅在cmcl配置文件不存在的情况下初始化
        if (!File("./cmcl.json").exists()) {
            Runtime.getRuntime().exec("./cmcl.exe config downloadSource 2").waitFor()
            Runtime.getRuntime().exec("./cmcl.exe version --isolate").waitFor()
        }

        val process =
            Runtime.getRuntime().exec("./cmcl.exe install $minecraftVersion -n \"$version\" $modLoaderInstallArg")
        val reader = BufferedReader(InputStreamReader(process.inputStream, "GB2312"))

        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            println(line)
        }

        val exitCode = process.waitFor()
        if (exitCode == 0) {
            println()
            println("安装成功！".green())
        } else {
            println()
            exitWithHint("安装失败，请重试或检查配置文件".red())
        }
    }

    if (globalConfig.minecraft.syncConfig) {
        val configFile = File("./.minecraft/versions/$version/config-$version.zip")
        if (configFile.exists()) return
        println("正在下载配置文件".cyan())
        val configFileData = "$server/config-$version.zip".httpGet().awaitByteArrayResponseResult().third.fold(
            { data -> data },
            { error -> exitWithHint("下载配置文件 $version 出错 ：$error".red()) }
        )

        File("./.minecraft/versions/$version/config-$version.zip").writeBytes(configFileData)
        unzip(configFile, File("./.minecraft/versions/$version"))
    }
}

fun generateSyncInfo() {
    val version = requireStringOrDefault(
        "请输入要生成同步文件的版本（默认为${globalConfig.minecraft.version}）",
        globalConfig.minecraft.version
    ) {
        it in getVersionList().map { v -> v.name }
    }
    val versionInfo = getVersionInfo(version)

    val syncDir = File("./MSN/sync/$version")
    syncDir.deleteRecursively()
    syncDir.mkdirs()
    File("./MSN/output/$version").mkdirs()

    copyFolder("./.minecraft/versions/$version", "./MSN/sync/$version")

    File("./MSN/sync/$version/mods").deleteRecursively()
    File("./MSN/sync/$version/custommods").deleteRecursively()
    File("./MSN/sync/$version/logs").deleteRecursively()
    File("./MSN/sync/$version/$version-natives").deleteRecursively()
    File("./MSN/sync/$version/$version.jar").delete()
    File("./MSN/sync/$version/$version.json").delete()

    File("./MSN/sync/$version").listFiles()?.forEach {
        val files = it.listFiles()
        if (it.isDirectory && (files == null || files.isEmpty())) {
            it.delete()
        }
    }

    println("请在 MSN/sync/$version 文件夹里删除不需要同步的配置文件，然后按回车继续".yellow())
    readln()

    println("正在生成同步文件...".yellow())
    zipFolder(File("./MSN/sync/$version"), File("./MSN/output/$version/config.zip"))
    copyFolder("./.minecraft/versions/$version/mods", "./MSN/output/$version/mods")
    File("./MSN/output/$version/version-info.json").writeText(Json.encodeToString(versionInfo))

    zipFolder(File("./MSN/output/$version"), File("./MSN/output/sync-$version.zip"))
    File("./MSN/output/$version").deleteRecursively()

    println("生成同步文件成功！请查看 MSN/output/sync-$version.zip\n".green())
}


suspend fun syncMod(version: String) {
    if (globalConfig.minecraft.isolate) ensureVersionExist(version)
    readFileHashCache()

    val minecraftPath = if (globalConfig.minecraft.isolate) "$versionDir/$version" else ".minecraft"
    val modDir = "$minecraftPath/mods"

    println("正在校验本地mod...".cyan())
    val modsHash = computeAllHashForFolder(modDir)

    println("正在校验自定义mod...".cyan())
    val customModsHash = computeAllHashForFolder("$minecraftPath/custommods")
    customModsHash.printModsInfo()

    println("正在获取mod列表...".cyan())
    val result = "$server/filelist-$version.csv".httpGet().awaitStringResponseResult().third

    val csv = result.fold(
        { data -> data },
        { error -> exitWithHint("获取mod列表出错：$error".red()) }
    )
    val serverModsHash = csv.split("\n").map { it.split(",").reversed() }.associate { it[0] to it[1] }

    val modsToAdd = serverModsHash - modsHash.keys - customModsHash.keys
    val modsToRemove = modsHash - serverModsHash.keys - customModsHash.keys
    val modsToCopy = customModsHash - modsHash.keys

    println("[要删除的mod列表]".red())
    modsToRemove.printModsInfo()

    println("[要下载的mod列表]".green())
    modsToAdd.printModsInfo()

    println()
    println(">>> 同步开始".cyan())

    println("1. 开始删除mod".red())
    var i = 1
    for (fileName in modsToRemove.values) {
        println("[$i/${modsToRemove.size}] $fileName")
        File("$modDir/$fileName").delete()
        fileHashCache.remove("$modDir/$fileName")
        i++
    }

    println("2. 开始下载mod".green())
    i = 1
    for ((hash, fileName) in modsToAdd) {
        println("[$i/${modsToAdd.size}] $fileName")
        val path = "$modDir/$fileName"
        val data = "$server/$version/${
            withContext(Dispatchers.IO) {
                URLEncoder.encode(fileName, "UTF-8")
            }
        }".httpGet().awaitByteArrayResponseResult().third.fold(
            { data -> data },
            { error -> exitWithHint("下载mod出错：$error".red()) }
        )
        val newFile = File(path)
        newFile.writeBytes(data)
        fileHashCache[path] = FileInfo(newFile.length(), newFile.lastModified(), hash)
        i++
    }

    println("3. 开始复制本地mod".yellow())
    i = 1
    for (fileName in modsToCopy.values) {
        println("[$i/${modsToCopy.size}] $fileName")
        val modPath = "$minecraftPath/mods/$fileName"
        val customModPath = "$minecraftPath/custommods/$fileName"
        val newFile = File(modPath)
        newFile.writeBytes(File(customModPath).readBytes())
        fileHashCache[modPath] = FileInfo(newFile.length(), newFile.lastModified(), fileHashCache[customModPath]!!.hash)
        i++
    }

    writeFileHashCache()
}