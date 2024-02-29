package net.il

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility object for quickly creating files of a given type within the Android app's data directory
 * on the Android device itself
 *
 * @property BUFFER_SIZE size of the buffer to be used during file operations
 */
// TODO: KMP location of actual/expected for platform-specific file system code
object FSHelper {
    private const val BUFFER_SIZE = 2048

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
     * Constructs and returns a new, blank file for use elsewhere; NOTE*** this method does not call
     * createNewFile(), as this should be done by the caller
     *
     * @param directoryPath runtime path from which to retrieve the file
     * @param newFileName name to give the new file
     * @param newFilePath path within which to construct the new file
     * @param fileSizeInBytes size to construct the file with, as well as fill with 0x00
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
     */
    // TODO: add new file flag
    fun createZip(
        origin: File,
        dest: File
    ): File? = try {
        val out = ZipOutputStream(BufferedOutputStream(dest.outputStream()))
        val data = ByteArray(BUFFER_SIZE)
        val fi = FileInputStream(origin)
        val stream = BufferedInputStream(fi, BUFFER_SIZE)
        val entry = ZipEntry(dest.name)
        out.putNextEntry(entry)

        var count: Int
        while (stream.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
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