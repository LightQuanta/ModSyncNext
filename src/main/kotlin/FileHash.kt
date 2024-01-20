package tech.lq0.modSyncNext

import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

private fun getSha256(path: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(File(path).readBytes())
    return digest.joinToString("") { "%02X".format(it) }
}

private fun Map<String, String>.printModsInfo() =
    println(this.map { (k, v) -> v.yellow() + " -> " + k.blue() }.joinToString("\n").ifEmpty { "（无）" })

private fun computeAllHashForFolder(path: String): Map<String, String> {
    if (!File(path).exists()) File(path).mkdir()

    val mods = File(path).listFiles()?.filter { it.isFile }

    return mods?.associate {
        getSha256(it.absolutePath) to it.name
    } ?: emptyMap()
}

suspend fun syncMod(version: String) {
    val minecraftPath = if (globalConfig.minecraft.isolate) "$versionDir/$version" else ".minecraft"
    val modDir = "$minecraftPath/mods/"

    println("正在校验本地mod...".cyan())
    val modsHash = computeAllHashForFolder(modDir)

    println("正在校验自定义mod...".cyan())
    val customModsHash = computeAllHashForFolder("$minecraftPath/custommods/")
    customModsHash.printModsInfo()

    val server = globalConfig.sync.server.trim('/')

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
        File(modPath).writeBytes(File(customModPath).readBytes())
        i++
    }
}