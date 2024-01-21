package tech.lq0.modSyncNext

import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URLEncoder
import java.security.MessageDigest

private val server = globalConfig.sync.server.trim('/')

@Serializable
private data class FileInfo(
    val size: Long,
    val lastModified: Long,
    val hash: String,
)

private val fileHashCache = mutableMapOf<String, FileInfo>()

private fun getSha256(path: String): String {
    val file = File(path)
    if (fileHashCache.containsKey(path)) {
        val info = fileHashCache[path]!!
        if (info.size == file.length() && file.lastModified() == info.lastModified) return info.hash
        fileHashCache.remove(path)
    }
    val md = MessageDigest.getInstance("SHA-256")
    val hash = md.digest(file.readBytes()).joinToString("") { "%02X".format(it) }
    val info = FileInfo(file.length(), file.lastModified(), hash)

    fileHashCache[path] = info
    return hash
}

fun readFileHashCache() {
    if (!File("./MSN").exists()) File("./MSN").mkdir()
    if (!File("./MSN/hash.json").exists()) return

    val file = File("./MSN/hash.json")
    fileHashCache.putAll(Json.decodeFromString<Map<String, FileInfo>>(file.readText()))
}

fun writeFileHashCache() = File("./MSN/hash.json").writeText(Json.encodeToString(fileHashCache))

private fun Map<String, String>.printModsInfo() =
    println(this.map { (k, v) -> v.yellow() + " -> " + k.blue() }.joinToString("\n").ifEmpty { "（无）" })

private fun computeAllHashForFolder(path: String): Map<String, String> {
    val pathTrimmed = path.trimEnd('/', '\\')
    if (!File(pathTrimmed).exists()) File(pathTrimmed).mkdir()

    val mods = File(pathTrimmed).listFiles()?.filter { it.isFile }

    return mods?.associate {
        getSha256("$pathTrimmed/${it.name}") to it.name
    } ?: emptyMap()
}

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
        Runtime.getRuntime().exec("./cmcl.exe config downloadSource 2").waitFor()
        Runtime.getRuntime().exec("./cmcl.exe version --isolate").waitFor()
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

    println("[要下载的mod列表]".cyan())
    modsToAdd.printModsInfo()

    println("[要删除的mod列表]".red())
    modsToRemove.printModsInfo()

    println()
    println("[同步开始]".cyan())

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
    for (fileName in modsToAdd.values) {
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
        File(path).writeBytes(data)
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