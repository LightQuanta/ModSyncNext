package tech.lq0.modSyncNext

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun copyFolder(source: Path, destination: Path) {
    Files.createDirectories(destination)

    Files.walk(source).use { stream ->
        stream.forEach { sourcePath ->
            val relativePath = source.relativize(sourcePath)
            val destinationPath = destination.resolve(relativePath)

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
