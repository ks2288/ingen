@file:OptIn(ExperimentalPathApi::class)

package net.il.util

import net.il.util.FSHelper.BUFFER_SIZE
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

/**
 * Utility object for quickly creating files of a given type within the runtime
 * directory of the Ingen instance
 *
 * @property BUFFER_SIZE size of the buffer to be used during file operations
 */
// TODO: KMP actual/expected for platform-specific file system code
object FSHelper {
    private const val BUFFER_SIZE = 2048

    /**
     * Creates all directories at a given path and checks their existence using
     * redundant strategies
     *
     * @param path path through which to create all needed directories
     * @return success flag for directory creation
     */
    fun createPathDirectories(path: String): Boolean = try {
        File(path).mkdirs()
    } catch (e: Exception) {
        Logger.error(e)
        Paths.get(path).toFile().exists()
    }

    /**
     * Recursively copies files from a given source path to a given destination
     *
     * @param sourcePath path containing the files to be copied
     * @param destinationPath path to which the files will be copied
     * @return success flag for file existence on host system
     */
    fun copyFilesRecursively(
        sourcePath: String,
        destinationPath: String
    ): Boolean = try {
        val success = createPathDirectories(destinationPath)
        Logger.debug("Path directories created to $destinationPath: $success")
        Paths.get(destinationPath).copyToRecursively(
            target = Paths.get(sourcePath),
            followLinks = false
        )
        File(destinationPath).listFiles()?.isNotEmpty() ?: false
    } catch (e: Exception) {
        Logger.error(e)
        false
    }

    /**
     * Simple wrapper to safely load a file and read/return its text contents
     *
     * @param filePath path to file to be loaded/read from
     * @return string of the file's contents
     */
    fun getFileText(filePath: String): String? = try {
        File(filePath).readText()
    }catch (e: Exception) {
        Logger.error(e)
        null
    }

    /**
     * Constructs and returns a new, blank file for use elsewhere; NOTE*** this
     * method does not call createNewFile()
     *
     * @param directoryPath runtime path from which to retrieve the file
     * @param newFileName name to give the new file
     * @param newFilePath path within which to construct the new file
     * @param fileSizeInBytes size to give the file, filled with 0x00
     * @return blank file of a chosen size at the given path with the given name
     */
    fun getBlankFile(
        directoryPath: String,
        newFileName: String,
        newFilePath: String,
        fileSizeInBytes: Int? = null
    ): File? = try {
        val outputDir = File(directoryPath + newFilePath)
        outputDir.mkdirs()
        val f = File(outputDir, newFileName)
        fileSizeInBytes?.let {
            val b = ByteArray(it)
            for (i in 0 until it) {
                b[i] = 0x01
            }
            f.writeBytes(b)
        }
        f
    } catch (e: Exception) {
        Logger.error(e)
        null
    }


    /**
     * Compile zip file from file output stream, loaded per file name
     *
     * @param origin origin file whose contents are to be zipped
     * @param dest new file to place the zipped contents within
     * @return ZIP-formatted file of the original file contents
     */
    // TODO: add new file flag
    fun createZip(
        origin: File,
        dest: File
    ): File? = try {
        val out = ZipOutputStream(BufferedOutputStream(dest.outputStream()))
        val data = ByteArray(BUFFER_SIZE)
        val fis = FileInputStream(origin)
        val stream = BufferedInputStream(fis, BUFFER_SIZE)
        val entry = ZipEntry(dest.name)
        out.putNextEntry(entry)

        var count: Int
        while (stream.read(data, 0, BUFFER_SIZE)
                .also { count = it } != -1) {
            Logger.debug("Writing data to zip with size: $count")
            out.write(data, 0, count)
        }

        stream.close()
        out.close()
        dest
    } catch (e: Exception) {
        Logger.error(e)
        null
    }
}
