package tech.lq0.modSyncNext

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class FileInfo(
    val size: Long,
    val lastModified: Long,
    val hash: String,
)

val fileHashCache = mutableMapOf<String, FileInfo>()
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

fun computeAllHashForFolder(path: String): Map<String, String> {
    val pathTrimmed = path.trimEnd('/', '\\')
    if (!File(pathTrimmed).exists()) File(pathTrimmed).mkdir()

    val mods = File(pathTrimmed).listFiles()?.filter { it.isFile }

    return mods?.associate {
        getSha256("$pathTrimmed/${it.name}") to it.name
    } ?: emptyMap()
}