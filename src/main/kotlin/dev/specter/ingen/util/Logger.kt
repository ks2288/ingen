package dev.specter.ingen.util

import java.io.File
import java.io.FileNotFoundException
import java.util.*

// TODO: fill this out or switch to something like Timber
object Logger {

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
    fun logToFile(
        text: String,
        args: List<String>,
        directory: String,
        commandName: String,
        logName: String? = "ingen_splog"
    ) {
        val sb = StringBuilder()
        sb.appendLine(
            """
            Begin log for subprocess:
                command: $commandName
                working dir: $directory
                args:
        """.trimIndent()
        )
        args.forEachIndexed { i, s ->
            sb.appendLine("     Arg $i: $s")
        }

        sb.appendLine("\n*** Begin subprocess output ***\n")

        text.lines().forEach { sb.appendLine(it) }
        try {
            val file = File(
                // TODO: combine this property with those in config
                CommandConstants.LOG_DIR,
                createLogFileName(logName = logName ?: commandName)
            )
            file.bufferedWriter().use { out ->
                out.write(sb.toString())
            }
        } catch (e: FileNotFoundException) {
            error("Error saving log to file: ${e.localizedMessage}")
        }
    }

    /**
     * Creates a log file name per executed command and returns a path string
     *
     * @param logName name to prepend the file path with
     * @return timestamped path string
     */
    private fun createLogFileName(logName: String = "ingen_splog"): String {
        return "$${logName}_${Calendar.getInstance().time}.txt"
            .replace(" ", "_")
            .replace("/", "-")
    }
}
