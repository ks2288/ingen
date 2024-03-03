package net.il.util

import java.io.File
import java.io.FileNotFoundException
import java.util.*

// TODO: fill in methods with actual logging logic (or switch to something like Timber)
object Logger {
    private val USER_HOME = System.getProperty("user.home")
    private const val LOG_DIRECTORY = ".fusion_log/"
    val LOG_PATH = "$USER_HOME/$LOG_DIRECTORY"

    fun debug(message: String) {
        println(message)
    }

    fun verbose(message: String) {
        println(message)
    }

    fun info(message: String) {
        println(message)
    }

    fun warning(e: Exception) {
        println(e.localizedMessage)
    }

    fun warning(message: String) {
        println(message)
    }

    fun error(message: String) {
        println(message)
    }

    fun error(e: Exception) {
        println(e.localizedMessage)
    }

    fun error(e: Throwable) {
        println(e.localizedMessage)
    }

    // TODO: change this parameter from the args list to some kind of descriptive title
    /**
     * Creates a log file name per executed command and returns a path string
     *
     * @param args executed command arguments
     * @return timestamped path string
     */
    private fun createLogFileName(args: List<String>): String {
        val arg = if (args.size > 1) args[1] else args[0]
        return "${Calendar.getInstance().time}_$arg.txt"
            .replace(" ", "_")
            .replace("/", "-")
    }

    /**
     * Writes an output of the given text to a log text file located on the
     * controller SoM
     * @param text string to be written to the file
     * @param args argument list for adding logging details
     * @param directory directory for log file to be written
     */
    private fun toFile(text: String, args: List<String>, directory: String) {
        val sb = StringBuilder()
        sb.appendLine("""
            Begin log for subprocess:
                working dir: $directory
                args:
        """.trimIndent())
        args.forEachIndexed { i, s ->
            sb.appendLine("     Arg $i: $s")
        }

        sb.appendLine("\n*** Begin subprocess output ***\n")

        text.lines().forEach { sb.appendLine(it) }
        try {
            val file = File(
                LOG_PATH,
                createLogFileName(args = args)
            )
            file.bufferedWriter().use { out ->
                out.write(sb.toString())
            }
        } catch (e: FileNotFoundException) {
            println("Error saving log to file: ${e.localizedMessage}")
        }
    }
}
