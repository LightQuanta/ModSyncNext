package tech.lq0.modSyncNext

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun copyFolder(source: String, destination: String) {
    val sourceDir = Paths.get(source)
    val destinationDir = Paths.get(destination)

    Files.createDirectories(destinationDir)

    Files.walk(sourceDir).use { stream ->
        stream.forEach { sourcePath ->
            val relativePath = sourceDir.relativize(sourcePath)
            val destinationPath = destinationDir.resolve(relativePath)

            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(destinationPath)
            } else {
                Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

fun zipFolder(sourceFolder: File, zipFile: File) {
    val fos = FileOutputStream(zipFile)
    val zos = ZipOutputStream(fos)

    try {
        zipRecursive(sourceFolder, sourceFolder, zos)
    } finally {
        zos.close()
        fos.close()
    }
}

fun unzip(zipFile: File, destinationFolder: File) {
    val fis = FileInputStream(zipFile)
    val zis = ZipInputStream(fis)

    try {
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val entryFile = File(destinationFolder, entry.name)

            if (entry.isDirectory) {
                entryFile.mkdirs()
            } else {
                entryFile.parentFile.mkdirs()
                FileOutputStream(entryFile).use { fos ->
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                }
            }

            zis.closeEntry()
            entry = zis.nextEntry
        }
    } finally {
        zis.close()
        fis.close()
    }
}

private fun zipRecursive(baseFolder: File, sourceFile: File, zos: ZipOutputStream) {
    val relativePath = baseFolder.toURI().relativize(sourceFile.toURI()).path

    if (sourceFile.isDirectory) {
        val entries = sourceFile.listFiles()
        if (entries != null) {
            for (entry in entries) {
                zipRecursive(baseFolder, entry, zos)
            }
        }
    } else {
        FileInputStream(sourceFile).use { fis ->
            val zipEntry = ZipEntry(relativePath)
            zos.putNextEntry(zipEntry)

            val buffer = ByteArray(1024)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zos.write(buffer, 0, length)
            }

            zos.closeEntry()
        }
    }
}
