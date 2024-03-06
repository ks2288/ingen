@file:OptIn(DelicateCoroutinesApi::class)

package net.il

import command.ConfigBuilder
import command.ISubprocess
import command.Session
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import net.il.util.CommandConstants
import net.il.util.Logger
import java.io.*
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.*

class Commander {
    //region Properties

    private val commands = ConfigBuilder.buildCommands()
    private val config = ConfigBuilder.buildConfig()
    private val sessions = arrayListOf<Session>()

    //endregion

    //region Class Functions

    /**
     * Executes an environment process via the JRE with the given args
     *
     * @param executable subprocess command object
     * @param userArgs string array containing the process args
     * @return text of process's stdout
     */
    fun execute(
        executable: ISubprocess,
        userArgs: List<String>,
    ): String {
        val outputBuilder = StringBuilder()
        val logBuilder = StringBuilder()
        val wdp = getWorkingDirectoryPath(executable)
        val args = buildArguments(executable, userArgs)
        try {
            val pb = buildProcess(workingDir = wdp, args = args)
            val process = pb.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader =
                BufferedReader(InputStreamReader(process.errorStream))

            reader.forEachLine {
                Logger.debug("[CMDR] Subprocess output received: $it")
                logBuilder
                    .appendLine("${Calendar.getInstance().time}: $it")
                outputBuilder.appendLine(it)
            }
            errorReader.forEachLine {
                Logger.debug("[CMDR] Subprocess error received: $it")
                logBuilder
                    .appendLine("${Calendar.getInstance().time}: $it")
                outputBuilder.appendLine(it)
            }

            val exitVal = process.waitFor()
            with("Subprocess exited with code: $exitVal") {
                Logger.debug(this)
                logBuilder
                    .appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } catch (e: Exception) {
            val s = "[CMDR] Subprocess exited with error: ${e.localizedMessage}"
            val c = Calendar.getInstance().time
            with(s) {
                Logger.error(this)
                logBuilder.appendLine("${c}: $this")
                outputBuilder.appendLine(this)
            }

        } finally {
            logToFile(
                logBuilder.toString(),
                userArgs, wdp,
                executable.id.toString()
            )
        }
        return outputBuilder.toString()
    }

    /**
     * Spawns a subprocess and exposes a flow channel to asynchronously report
     * subprocess I/O and errors to the calling collector; NOTE: unless the
     * collecting scope/context are properly layered/preserved, this channel
     * will function as TERMINAL, in that it will execute your command, but the
     * collected results will only be reported when the subprocess has exited;
     * realistically, this should only be used within a coroutine-based
     * framework (such as KTOR) that can guarantee scope preservation (i.e. not
     * Compose)
     *
     * @param executable subprocess command object
     * @param userArgs string array containing the process args
     * @return coroutine channel flow for monitoring subprocess output
     */
    fun executeChannelFlow(
        executable: ISubprocess,
        userArgs: List<String>,
    ) = channelFlow {
        val sb = StringBuilder()
        val wdp = getWorkingDirectoryPath(executable)
        val args = buildArguments(executable, userArgs)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                redirectInput = true,
                redirectError = true
            )

            val process = pb.start()
            sessions.add(Session(process, executable.id))

            process.inputStream.bufferedReader().forEachLine { string ->
                println("[CMDR] CGI process output received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.trySend(string) }
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                println("[CMDR] CGI process error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.trySend(string) }
            }

            val exitVal = process.waitFor()
            with("Subprocess exited with code: $exitVal") {
                println(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }

        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            with(s) {
                println(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } finally {
            logToFile(sb.toString(), userArgs, wdp, executable.id.toString())
            endSession(executable)
            close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Monitors asynchronous system program execution/output spawned with
     * [ProcessBuilder] via coroutines
     *
     * @param executable subprocess command object
     * @param userArgs string array containing the process args
     * @param channel receiver for subsequent commands
     * @return channel flow of output fed to the route monitor
     */
    suspend fun collectAsync(
        executable: ISubprocess,
        userArgs: List<String>,
        channel: SendChannel<String>,
    ) = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val wdp = getWorkingDirectoryPath(executable)
        val args = buildArguments(executable, userArgs)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                redirectInput = true,
                redirectError = true
            )

            val process = pb.start()
            sessions.add(Session(process, executable.id))

            process.inputStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] CGI process output received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.send(string) }
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] CGI process error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.send(string) }
            }

            val exitVal = process.waitFor()
            with("Subprocess exited with code: $exitVal") {
                Logger.debug(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
            return@withContext
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            with(s) {
                Logger.error(e)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } finally {
            logToFile(sb.toString(), args, wdp, executable.id.toString())
            endSession(executable)
            channel.close()
            endSession(executable)
        }
    }

    /**
     * Monitors I/O from a given subprocess via redirection and publishes both
     * output (process STDIN) and errors (process STDERR) to Rx observers while
     * also logging to file per command on the runtime host
     *
     * @param executable object containing all necessary command info
     * @param userArgs string array containing the process args
     * @param outputPublisher output route for subprocess STDOUT
     */
    fun executeRx(
        executable: ISubprocess,
        userArgs: List<String>,
        outputPublisher: BehaviorProcessor<String>,
    ) {
        val sb = StringBuilder()
        val args = buildArguments(executable, userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                redirectInput = true,
                redirectError = true
            )

            val process = pb.start()
            sessions.add(Session(process, executable.id))

            process.inputStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] Subprocess output received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] Subprocess error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = process.waitFor()
            outputPublisher.onComplete()
            val s = "Subprocess exited with code: $exitVal"
            Logger.debug(s)
            sb.appendLine("\n${Calendar.getInstance().time}: $s")
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            Logger.error(s)
            sb.appendLine("\n${Calendar.getInstance().time}: $s")
            outputPublisher.onError(e)
        } finally {
            logToFile(sb.toString(), userArgs, wdp, executable.id.toString())
            endSession(executable)
        }
    }

    /**
     * Spawns an asynchronous, interactive subprocess that includes an input
     * publishing route for sending data to the subprocess during its runtime
     *
     * @param executable subprocess command object
     * @param userArgs command arguments
     * @param inputPublisher input route for subprocess STDIN
     * @param outputPublisher output route for subprocess STDOUT
     * @param receiverScope coroutine scope of caller
     */
    fun executeRxInteractive(
        executable: ISubprocess,
        userArgs: List<String>,
        inputPublisher: BehaviorProcessor<String>,
        outputPublisher: BehaviorProcessor<String>,
        receiverScope: CoroutineScope = GlobalScope,
    ) {
        val sb = StringBuilder()
        val args = buildArguments(executable, userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = buildProcess(workingDir = wdp, args = args)
            val process = pb.start()
            sessions.add(Session(process, executable.id))

            // background job for accepting input and writing to subproc STDIN
            receiverScope.launch {
                inputPublisher.subscribe { sig ->
                    if (sig == "SIGKILL") {
                        process.outputStream.close()
                        process.inputStream.close()
                        process.errorStream.close()
                        process.destroyForcibly()
                    }
                    println("Input queued: $sig")
                    with(process.outputStream.bufferedWriter()) {
                        write(sig)
                        newLine()
                        flush()
                    }
                }
            }

            process.inputStream.bufferedReader().forEachLine { string ->
                if (string.isNotBlank()) {
                    Logger.debug("[CMDR] CGI process output received: $string")
                    val s = "${Calendar.getInstance().time}: $string"
                    sb.appendLine(s)
                    outputPublisher.onNext(string)
                }
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] CGI process error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = process.waitFor()

            val s = "Subprocess exited with code: $exitVal"
            Logger.debug(s)
            sb.appendLine("\n${Calendar.getInstance().time}: $s")

            outputPublisher.onComplete()
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            Logger.error(s)
            sb.appendLine("\n${Calendar.getInstance().time}: $s")
        } finally {
            logToFile(sb.toString(), userArgs, wdp, executable.id.toString())
            endSession(executable)
        }
    }

    /**
     * Spawns a watch service loop that can be used to continuously monitor a
     * given path on the host system for file changes
     *
     * @param executable subprocess command object
     * @param args full list of command arguments
     * @param watchDirectory directory to be watched for file changes
     * @param outputPublisher output route for subprocess STDOUT
     * @param channel input channel for external loop control
     */
    fun spawnWatcherCycle(
        executable: ISubprocess,
        args: List<String>,
        watchDirectory: String,
        outputPublisher: BehaviorProcessor<String>,
        channel: Channel<Int>,
    ) {
        var hold = true
        GlobalScope.launch {
            channel.consumeAsFlow().collect {
                println("Control channel signal received: $it")
                hold = false
            }
        }

        while (hold) {
            executeFileWatch(
                executable,
                args,
                watchDirectory,
                outputPublisher,
            )
        }

        endSession(executable)
    }

    /**
     * Spawns an async file watch service that grabs input created by the QR
     * scanner hardware, and reports it to the observer on subproc conclusion;
     * this method DOES NOT work in the same way as [executeRx]; rather,
     * it works identically to [collectAsync] in that the flow is cold,
     * and it will only return values when the subprocess exits; in other words,
     * this is NOT a continuous monitor, and it needs to be re-initiated after
     * each read; to use this functionality as a continuous service, use
     * [spawnWatcherCycle]
     *
     * @param executable subprocess command object
     * @param userArgs full list of command arguments
     * @param watchDirectory directory to be watched for file changes
     * @param outputPublisher output route for subprocess STDOUT
     */
    fun executeFileWatch(
        executable: ISubprocess,
        userArgs: List<String>,
        watchDirectory: String,
        outputPublisher: BehaviorProcessor<String>,
    ) {
        val logBuilder = StringBuilder()
        val args = buildArguments(executable, userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                redirectInput = true,
                redirectError = true
            )

            val process = pb.start()
            with(process) {
                println(
                    """
                    Subprocess created:
                        PID: ${pid()}
                        Command: ${info().command()}
                """.trimIndent()
                )
                sessions.add(Session(this, executable.id))
            }

            val watchService = FileSystems.getDefault().newWatchService()
            val watchPath = File(watchDirectory).toPath()
            val keys = watchPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            val watchKey = watchService.take()

            var s = ""
            watchKey.pollEvents().first().let {
                val fileDir = "$watchDirectory/${it.context()}"
                val f = File(fileDir)
                f.deleteOnExit()
                f.reader().forEachLine { str ->
                    println("[CMDR] Watch service output received: $str")
                    val out = "${Calendar.getInstance().time}: $str"
                    logBuilder.appendLine(out)
                    s += str
                }
            }
            watchKey.reset()
            watchKey.cancel()
            watchService.close()
            keys.cancel()
            val exitVal = process.waitFor()
            outputPublisher.onNext(s)

            val str = "\nSubprocess exited with code: $exitVal"
            Logger.debug(str)
            logBuilder.appendLine(str)
            outputPublisher.onComplete()
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            val c = Calendar.getInstance().time
            println(s)
            logBuilder.appendLine("\n${c}: $s")
            outputPublisher.onError(e)
        } finally {
            logToFile(
                logBuilder.toString(),
                args,
                wdp,
                executable.id.toString()
            )
            endSession(executable)
        }
    }

    /**
     * Forcibly destroys any laggard processes; not intended for graceful
     * termination, which should be handled by [endSession]
     */
    fun killAll() {
        sessions.forEach {
            it.process.destroyForcibly()
            val pid = it.process.pid()
            Logger.debug("Subprocess destroyed forcibly with PID: $pid")
        }
        sessions.clear()
    }

    //endregion

    //region Private Class Functions

    /**
     * Takes a path code and retrieves a command path from the config values
     *
     * @param code path code for the given command
     * @return path of program to be executed per [IProgram.code]
     */
    private fun getCommandPath(code: Int): String =
        config?.paths?.values
            ?.first { it.code == code }
            ?.path
            ?: throw RuntimeException("Cannot retrieve command path...")

    /**
     * Gets the working directory [File] object of the given command by
     * appending the nested directory path to the runtime directory's path
     * string
     *
     * @param ex subprocess command used to compound a working directory
     * @return runtime execution path needed by a given subprocess
     */
    private fun getWorkingDirectoryPath(ex: ISubprocess) : String {
        config?.let {
            return with(arrayListOf<String>()) {
                add(it.runtimeDirectory)
                add(ex.command.directory)
                filterNot { it.isBlank() }
                val sb = StringBuilder()
                forEach {  s -> sb.append(s) }
                val s = sb.toString()
                Logger.debug("Subprocess working directory: $s")
                s
            }
        } ?: throw RuntimeException("Cannot retrieve IngenConfig...")
    }

    /**
     * Ends a known session by destroying the JVM subprocess by ID
     *
     * @param ex subprocess to cross-reference the session ID from
     */
    private fun endSession(ex: ISubprocess) {
        try {
            val ended = sessions.first { s -> ex.id == s.subprocessId }
            val pid = ended.process.pid()
            ended.process.destroy()
            sessions.remove(ended)
            val s =
                "Subprocess terminated\n\tID: $pid\n\tTAG: ${ex.command.tag}\n"
            Logger.debug(s)
        } catch (e: Exception) { Logger.error(e) }
    }

    /**
     * Modular function for building a process for all types of sessions
     *
     * @param workingDir working directory from which to spawn the process
     * @param args full set of built arguments, including needed paths
     * @param redirectInput process input (STDIO) redirection flag
     * @param redirectOutput process output (STDIO) redirection flag
     * @param redirectError process error (STDERR) redirection flag
     * @return process builder from which the subprocess will be spawned
     */
    @Throws(IOException::class, FileNotFoundException::class)
    private fun buildProcess(
        workingDir: String,
        args: List<String>,
        redirectInput: Boolean = false,
        redirectOutput: Boolean = false,
        redirectError: Boolean = false
    ): ProcessBuilder {
        val wdf = File(workingDir)
        if (wdf.exists().not()) throw FileNotFoundException()
        return with(ProcessBuilder()) {
            if (redirectInput) redirectInput()
            if (redirectOutput) redirectOutput()
            if (redirectError) redirectError()
            directory(wdf)
            command(args)
        }
    }

    /**
     * Builds an arguments list to be passed to the active [ProcessBuilder],
     * including both the program path (i.e. /bin/echo), any corresponding
     * alias values (a python script file name, for example) and any
     * user-supplied arguments
     *
     * @param ex subprocess command to build arguments from
     * @param userArgs any user-supplied arguments to accompany the command
     * @return list of argument strings passed to the native [ProcessBuilder]
     */
    private fun buildArguments(
        ex: ISubprocess,
        userArgs: List<String>
    ) : List<String> {
        val argsList = ex.toArgsList(userArgs)
        val path = getCommandPath(ex.command.pathCode)
        return with(arrayListOf<String>()) {
            add(path)
            addAll(argsList)
            this.filter { it.isNotBlank() }
        }
    }

    /**
     * Writes an output of the given text to a log text file located on the
     * controller SoM
     * @param text string to be written to the file
     * @param args argument list for adding logging details
     * @param directory directory for log file to be written
     */
    // TODO: move to using Logger object method
    private fun logToFile(
        text: String,
        args: List<String>,
        directory: String,
        commandName: String
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
                createLogFileName(logName = commandName)
            )
            file.bufferedWriter().use { out ->
                out.write(sb.toString())
            }
        } catch (e: FileNotFoundException) {
            println("Error saving log to file: ${e.localizedMessage}")
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

    //endregion
}
