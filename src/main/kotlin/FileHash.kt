package tech.lq0.modSyncNext

import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.system.exitProcess

private fun getSha256(path: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(File(path).readBytes())
    return digest.joinToString("") { "%02X".format(it) }
}

private fun Map<String, String>.printModsInfo() =
    println(
        this.map { (k, v) -> "\u001B[33m$v\u001B[0m -> \u001B[34m$k\u001B[0m" }.joinToString("\n").ifEmpty { "（无）" })

private fun computeAllHashForFolder(path: String): Map<String, String> {
    if (!File(path).exists()) File(path).mkdir()

    val mods = File(path).listFiles()?.filter { it.isFile }

    return mods?.associate {
        getSha256(it.absolutePath) to it.name
    } ?: emptyMap()
}

suspend fun syncMod(version: String) {
    val modDir = "$baseDir/$version/mods/"
    val modsHash = computeAllHashForFolder(modDir)
    val customModsHash = computeAllHashForFolder("$baseDir/$version/custommods/")

    println("\u001B[36m[自定义mod]\u001B[0m")
    customModsHash.printModsInfo()

    val server = globalConfig.sync.server

    val result = "$server/filelist-$version.csv".httpGet().awaitStringResponseResult().third

    val csv = result.fold(
        { data -> data },
        { error ->
            println("\u001B[31m获取mod列表出错：$error\u001B[0m")
            println("按回车退出")
            readln()
            exitProcess(0)
        }
    )
    val serverModsHash = csv.split("\n").map { it.split(",").reversed() }.associate { it[0] to it[1] }

    val modsToAdd = serverModsHash - modsHash.keys - customModsHash.keys
    val modsToRemove = modsHash - serverModsHash.keys - customModsHash.keys
    val modsToCopy = customModsHash - modsHash.keys

    println("\u001b[36m[下载mod列表]\u001B[0m")
    modsToAdd.printModsInfo()

    println("\u001B[36m[删除mod列表]\u001B[0m")
    modsToRemove.printModsInfo()

    println("\u001B[32m1. 开始下载mod\u001B[0m")
    var i = 1
    for (fileName in modsToAdd.values) {
        println("[$i/${modsToAdd.size}] $fileName")
        val path = "$modDir/$fileName"
        val data = "$server/$version/${
            withContext(Dispatchers.IO) {
                URLEncoder.encode(fileName, "UTF-8")
            }
        }".httpGet().awaitByteArrayResponseResult().third.fold(
            { data -> data },
            { error ->
                println("\u001B[31m下载mod出错：$error\u001B[0m")
                println("按回车退出")
                readln()
                exitProcess(0)
            }
        )
        File(path).writeBytes(data)
        i++
    }

    println("\u001B[31m2. 开始删除mod\u001B[0m")
    i = 1
    for (fileName in modsToRemove.values) {
        println("[$i/${modsToRemove.size}] $fileName")
        File("$modDir/$fileName").delete()
        i++
    }

    println("\u001B[33m3. 开始复制本地mod\u001B[0m")
    i = 1
    for (fileName in modsToCopy.values) {
        println("[$i/${modsToCopy.size}] $fileName")
        val modPath = "$baseDir/$version/mods/$fileName"
        val customModPath = "$baseDir/$version/custommods/$fileName"
        File(modPath).writeBytes(File(customModPath).readBytes())
        i++
    }

    println("\u001B[36m同步完成！\u001B[0m")
}