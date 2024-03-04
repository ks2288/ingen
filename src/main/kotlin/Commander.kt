@file:OptIn(DelicateCoroutinesApi::class)

package net.il

import command.ConfigBuilder
import command.ISubprocess
import command.Session
import command.Subprocess
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import net.il.util.Logger
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
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
     * @param executable subprocess command object containing all necessary information
     * @param userArgs string array containing the process args
     * @return "stringified" response from process's stdout
     */
    fun execute(
        executable: ISubprocess,
        userArgs: List<String>,
    ): String {
        val sb = StringBuilder()
        val lb = StringBuilder()
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = ProcessBuilder()
            with(pb) {
                directory(File(wdp))
                Logger.debug("Working dir for proc builder: ${directory()}")
                val cmdArgs = buildArguments(
                    ex = executable,
                    userArgs = userArgs
                )
                command(cmdArgs)
            }

            val process = pb.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            reader.forEachLine {
                Logger.debug("[CMDR] Subprocess output received: $it")
                lb.appendLine("${Calendar.getInstance().time}: $it")
                sb.appendLine(it)
            }
            errorReader.forEachLine {
                Logger.debug("[CMDR] Subprocess error received: $it")
                lb.appendLine("${Calendar.getInstance().time}: $it")
                sb.appendLine(it)
            }

            val exitVal = process.waitFor()
            with("Subprocess exited with code: $exitVal") {
                Logger.debug(this)
                lb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } catch (e: Exception) {
            with("[CMDR] Subprocess exited with error: ${e.localizedMessage}") {
                Logger.error(this)
                lb.appendLine("${Calendar.getInstance().time}: $this")
                sb.appendLine(this)
            }

        } finally {
            logToFile(lb.toString(), userArgs, wdp, executable.id.toString())
        }
        return sb.toString()
    }

    /**
     * Monitors asynchronous system program execution/output spawned with [ProcessBuilder] via coroutines
     *
     * @param executable subprocess command object containing all necessary information
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
        val args = executable.toArgsList(userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = ProcessBuilder()
            val wdf = File(wdp)

            with(pb) {
                directory(wdf)
                redirectInput()
                redirectError()
                Logger.debug("Working dir for proc builder: ${directory()}")
                command(args)
            }

            val proc = pb.start()
            sessions.add(Session(proc, executable.id))

            proc.inputStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] CGI process output received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.send(string) }
            }

            proc.errorStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] CGI process error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.send(string) }
            }

            val exitVal = proc.waitFor()
            with("Subprocess exited with code: $exitVal") {
                Logger.debug(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
            return@withContext
        } catch (e: Exception) {
            with("Subprocess exited with error: ${e.localizedMessage}") {
                Logger.error(e)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } finally {
            args?.let {
                logToFile(sb.toString(), it, wdp, executable.id.toString())
                endSession(executable)
                channel.close()
            }
            endSession(executable)
        }
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
     * @param executable subprocess command object containing all necessary information
     * @param userArgs string array containing the process args
     * @return coroutine channel flow for monitoring subprocess output
     */
    fun subprocessChannelFlow(
        executable: ISubprocess,
        userArgs: List<String>,
    ) = channelFlow {
        val sb = StringBuilder()
        val args = executable.toArgsList(userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = ProcessBuilder()
            val dirFile = File(wdp)

            with(pb) {
                directory(dirFile)
                redirectInput()
                redirectError()
                println("Working dir for proc builder: ${directory()}")
                command(args)
            }

            val proc = pb.start()
            sessions.add(Session(proc, executable.id))

            proc.inputStream.bufferedReader().forEachLine { string ->
                println("[CMDR] CGI process output received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.trySend(string) }
            }

            proc.errorStream.bufferedReader().forEachLine { string ->
                println("[CMDR] CGI process error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                launch { channel.trySend(string) }
            }

            val exitVal = proc.waitFor()
            with("Subprocess exited with code: $exitVal") {
                println(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }

        } catch (e: Exception) {
            with("Subprocess exited with error: ${e.localizedMessage}") {
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
     * Monitors I/O from a given subprocess via redirection and publishes both
     * output (process STDIN) and errors (process STDERR) to Rx observers while
     * also logging to file per command on the runtime host
     *
     * @param executable object containing all necessary command info
     * @param userArgs string array containing the process args
     * @param outputPublisher output route for subprocess STDOUT
     */
    fun monitorSubprocess(
        executable: ISubprocess,
        userArgs: List<String>,
        outputPublisher: BehaviorProcessor<String>,
    ) {
        val sb = StringBuilder()
        val args = executable.toArgsList(userArgs)
        val dir = (executable as Subprocess).command.directory
        val pb = ProcessBuilder()
        val dirFile = File(dir)
        try {
            with(pb) {
                directory(dirFile)
                redirectInput()
                redirectError()
                println("Subprocess working directory: $dir")
                command(args)
            }

            val proc = pb.start()
            sessions.add(Session(proc, executable.id))

            proc.inputStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] Subprocess output received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            proc.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] Subprocess error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = proc.waitFor()
            outputPublisher.onComplete()

            with("Subprocess exited with code: $exitVal") {
                Logger.debug(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } catch (e: Exception) {
            with("Subprocess exited with error: ${e.localizedMessage}") {
                Logger.error(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
            outputPublisher.onError(e)
        } finally {
            logToFile(sb.toString(), userArgs, dir, executable.id.toString())
            endSession(executable)
        }
    }

    /**
     * Spawns an asynchronous, interactive subprocess that includes an input
     * publishing route for sending data to the subprocess during its runtime
     *
     * @param executable subprocess command object containing all necessary information
     * @param userArgs command arguments
     * @param inputPublisher input route for subprocess STDIN
     * @param outputPublisher output route for subprocess STDOUT
     * @param receiverScope coroutine scope of caller
     */
    fun monitorControlChannel(
        executable: ISubprocess,
        userArgs: List<String>,
        inputPublisher: BehaviorProcessor<String>,
        outputPublisher: BehaviorProcessor<String>,
        receiverScope: CoroutineScope = GlobalScope,
    ) {
        val sb = StringBuilder()
        val args = executable.toArgsList(userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val wdf = File(wdp)
            val pb = ProcessBuilder()

            with(pb) {
                directory(wdf)
                println("Subprocess working directory: $wdp")
                command(args)
            }

            val proc = pb.start()
            sessions.add(Session(proc, executable.id))

            // background job for accepting input and writing to subproc STDIN
            receiverScope.launch {
                inputPublisher.subscribe { sig ->
                    if (sig == "SIGKILL") {
                        proc.outputStream.close()
                        proc.inputStream.close()
                        proc.errorStream.close()
                        proc.destroyForcibly()
                    }
                    println("Input queued: $sig")
                    with(proc.outputStream.bufferedWriter()) {
                        write(sig)
                        newLine()
                        flush()
                    }
                }
            }

            proc.inputStream.bufferedReader().forEachLine { string ->
                if (string.isNotBlank()) {
                    println("[CMDR] CGI process output received: $string")
                    sb.appendLine("${Calendar.getInstance().time}: $string")
                    outputPublisher.onNext(string)
                }
            }

            proc.errorStream.bufferedReader().forEachLine { string ->
                println("[CMDR] CGI process error received: $string")
                sb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = proc.waitFor()

            with("Subprocess exited with code: $exitVal") {
                Logger.debug(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }

            outputPublisher.onComplete()
        } catch (e: Exception) {
            with("Subprocess exited with error: ${e.localizedMessage}") {
                Logger.error(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
        } finally {
            logToFile(sb.toString(), userArgs, wdp, executable.id.toString())
            endSession(executable)
        }
    }

    /**
     * Spawns a watch service loop that can be used to continuously monitor a
     * given path on the host system for file changes
     *
     * @param executable subprocess command object containing all necessary information
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
            spawnWatchService(
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
     * this method DOES NOT work in the same way as [monitorSubprocess]; rather,
     * it works identically to [collectAsync] in that the flow is cold,
     * and it will only return values when the subprocess exits; in other words,
     * this is NOT a continuous monitor, and it needs to be re-initiated after
     * each read; to use this functionality as a continuous service, use
     * [spawnWatcherCycle]
     *
     * @param executable subprocess command object containing all necessary information
     * @param userArgs full list of command arguments
     * @param watchDirectory directory to be watched for file changes
     * @param outputPublisher output route for subprocess STDOUT
     */
    fun spawnWatchService(
        executable: ISubprocess,
        userArgs: List<String>,
        watchDirectory: String,
        outputPublisher: BehaviorProcessor<String>,
    ) {
        val sb = StringBuilder()
        val args = executable.toArgsList(userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = ProcessBuilder()
            val wdf = File(wdp)

            with(pb) {
                directory(wdf)
                redirectInput()
                redirectError()
                with("Target dir for watch service: $watchDirectory") {
                    println(this)
                    sb.appendLine(this)
                }
                command(args)
            }

            val proc = pb.start()
            with(proc) {
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
                    sb.appendLine("${Calendar.getInstance().time}: $str")
                    s += str
                }
            }
            watchKey.reset()
            watchKey.cancel()
            watchService.close()
            keys.cancel()
            val exitVal = proc.waitFor()
            outputPublisher.onNext(s)

            with("\nSubprocess exited with code: $exitVal") {
                Logger.debug(this)
                sb.appendLine(this)
            }

            outputPublisher.onComplete()
        } catch (e: Exception) {
            with("Subprocess exited with error: ${e.localizedMessage}") {
                println(this)
                sb.appendLine("\n${Calendar.getInstance().time}: $this")
            }
            outputPublisher.onError(e)
        } finally {
            args?.let {
                logToFile(
                    sb.toString(),
                    it,
                    wdp,
                    executable.id.toString()
                )
            }
            endSession(executable)
        }
    }

    /**
     * Simply destroys all running processes
     */
    fun killAll() {
        sessions.forEach {
            it.process.destroy()
            println("Subprocess destroyed with PID: ${it.process.pid()}")
        }
        sessions.clear()
    }

    //endregion

    //region Private Class Functions

    /**
     * Takes a path code and retrieves a command path from the config values
     *
     * @param code path code for the given command
     * @return path of program to be executed, per the configuration values provided per program
     */
    private fun getCommandPath(code: Int): String =
        config?.paths?.values
            ?.first { it.code == code }
            ?.path
            ?: throw RuntimeException("Cannot retrieve command path...")

    /**
     * Gets the working directory [File] object of the given command by appending the nested directory path to the
     * runtime directory's path string
     *
     * @param ex subprocess command for which to determine the correct working directory
     * @return file object for the target working directory
     */
    private fun getWorkingDirectoryPath(ex: ISubprocess) : String {
        return if (ex.command.directory.isNotBlank()) {
            "${config?.runtimeDirectory}${ex.command.directory}"
        } else config?.runtimeDirectory
            ?: throw RuntimeException("Cannot retrieve working dir...")
    }

    private fun endSession(ex: ISubprocess) {
        try {
            val ended = sessions.first { s -> ex.id == s.subprocessId }
            val pid = ended.process.pid()
            ended.process.destroy()
            sessions.remove(ended)
            Logger.debug("Subprocess terminated\n\tID: $pid\n\tTAG: ${ex.command.tag}\n")
        } catch (e: Exception) { Logger.error(e) }
    }

    /**
     * Builds an arguments list to be passed to the active [ProcessBuilder], including both the program path
     * (i.e. /bin/echo), any corresponding alias values (a python script file name, for example) and any user-supplied
     * arguments
     *
     * @param ex subprocess command to build arguments from
     * @param userArgs any user-supplied arguments to accompany the command
     * @return list of argument strings to be passed to the native [ProcessBuilder.command] property
     */
    private fun buildArguments(
        ex: ISubprocess,
        userArgs: List<String>
    ) : List<String> {
        val argsList = ex.toArgsList(userArgs)
        val path = getCommandPath(ex.command.pathCode)
        return with(arrayListOf<String>()) {
            add(path)
            argsList?.let { addAll(it) }
            this
        }
    }

    /**
     * Writes an output of the given text to a log text file located on the
     * controller SoM
     * @param text string to be written to the file
     * @param args argument list for adding logging details
     * @param directory directory for log file to be written
     */
    // TODO: move this to the still-needed logger class implementation
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
                LOG_PATH,
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
    private fun createLogFileName(logName: String = "viper_splog"): String {
        return "$${logName}_${Calendar.getInstance().time}.txt"
            .replace(" ", "_")
            .replace("/", "-")
    }
    //endregion

    companion object {
        private val USER_HOME = System.getProperty("user.home")
        private const val LOG_DIRECTORY = ".viper_log/"
        private val LOG_PATH = "$USER_HOME/$LOG_DIRECTORY"
    }
}
