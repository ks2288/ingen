@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.CommandConstants.SIG_KILL
import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.*
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.*

/**
 * Class for handling all sand-boxed subprocesses via the JDK's
 * [ProcessBuilder], with support for both Rx-based behavioral and
 * Coroutine-based asynchronous result routing; since [ProcessBuilder]
 * handles the waiting for a process termination signal in a semaphore-based
 * manner, most of these methods incorporate procedurally-oriented
 * sequencing; however, a more-thorough control of this class's execution
 * context is possible within the JRE by using manual threading to wrap
 * method calls; this way, frameworks that do not handle threading gracefully
 * (Java AWT and Swing, for example) can still handle these async operations
 * without freezing their main/UI thread while awaiting results
 *
 * @param configuration externally-passed configuration for extensibility
 * @property config configuration instance based on provided config file values
 * @property procMap list of all subprocess sessions for housekeeping
 */
class Commander(configuration: IngenConfig? = null) {

    //region Properties

    private val config: IngenConfig
    private val procMap: MutableMap<String, ArrayList<Process>> = mutableMapOf()

    //endregion

    //region Constructors

    /**
     * Explicit init ensures proper chronology for command configuration loading
     */
    init {
        ConfigBuilder.initializeFS()
        config = configuration ?: ConfigBuilder.buildConfig()
    }

    //endregion

    //region Class Functions

    /**
     * Executes an environment process via the JRE with the given args
     *
     * @param executable subprocess command object
     * @param userArgs string array containing the process args
     * @param env string map of any necessary environment variables for prime program
     * @param retainConfigEnvironment whether to retain all env vars from config file
     * @return text of process's stdout
     */
    fun execute(
        executable: ISubprocess,
        userArgs: List<String>,
        env: MutableMap<String, String> = mutableMapOf(),
        retainConfigEnvironment: Boolean = true
    ): String {
        val outputBuilder = StringBuilder()
        val logBuilder = StringBuilder()
        val wdp = getWorkingDirectoryPath(executable)
        val args = buildArguments(executable, userArgs)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                env = env,
                retainConfigEnvironment = retainConfigEnvironment
            )
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
            val name = getProgramName(executable)
            Logger.logToFile(
                logBuilder.toString(),
                userArgs,
                wdp,
                executable.callerKey.toString(),
                name
            )
        }
        return outputBuilder.toString()
    }

    /**
     * Executes an environment process via the JRE with the given args
     *
     * @param commandPath path to system command
     * @param args any necessary arguments, with any needed alias at index 0
     * @param env string map of any necessary environment variables for prime program
     * @param workingDir directory from which to spawn the subprocess
     * @param retainConfigEnvironment whether to retain all env vars from config file
     * @return text of process's stdout
     */
    fun executeExplicit(
        commandPath: String,
        args: List<String>,
        env: MutableMap<String, String> = mutableMapOf(),
        workingDir: String = IngenConfig.INGEN_DEFAULT_DIR,
        retainConfigEnvironment: Boolean = true
    ): String {
        val outputBuilder = StringBuilder()
        val logBuilder = StringBuilder()
        try {
            val cmdArgList = with(arrayListOf<String>()) {
                add(commandPath)
                addAll(args)
                this
            }
            val pb = buildProcess(
                workingDir = workingDir,
                args = cmdArgList,
                env = env,
                retainConfigEnvironment = retainConfigEnvironment
            )
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
            Logger.logToFile(
                text = logBuilder.toString(),
                args = args,
                directory = workingDir,
                commandId = "N/A",
                name = commandPath
            )
        }
        return outputBuilder.toString()
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
        env: MutableMap<String, String> = mutableMapOf(),
        outputPublisher: BehaviorProcessor<String>,
        retainConfigEnvironment: Boolean = true
    ) {
        val lb = StringBuilder()
        val args = buildArguments(executable, userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                env = env,
                redirectInput = true,
                redirectError = true,
                retainConfigEnvironment = retainConfigEnvironment
            )

            val process = pb.start()
            addSubprocessSession(executable.callerKey, process)

            process.inputStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] Subprocess output received: $string")
                lb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] Subprocess error received: $string")
                lb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = process.waitFor()
            outputPublisher.onComplete()
            val s = "Subprocess exited with code: $exitVal"
            Logger.debug(s)
            lb.appendLine("\n${Calendar.getInstance().time}: $s")
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            Logger.error(s)
            lb.appendLine("\n${Calendar.getInstance().time}: $s")
            outputPublisher.onError(e)
        } finally {
            val name = getProgramName(executable)
            Logger.logToFile(
                lb.toString(),
                userArgs,
                wdp,
                executable.callerKey.toString(),
                name
            )
            endSession(executable.callerKey)
        }
    }

    fun executeExplicitRx(
        commandPath: String,
        args: List<String>,
        workingDir: String,
        callerKey: String,
        env: MutableMap<String, String> = mutableMapOf(),
        outputPublisher: BehaviorProcessor<String>,
        retainConfigEnvironment: Boolean = true
    ) {
        val lb = StringBuilder()
        try {
            val cmdArgList = with(arrayListOf<String>()) {
                add(commandPath)
                addAll(args)
                this
            }

            val pb = buildProcess(
                workingDir = workingDir,
                args = cmdArgList,
                env = env,
                retainConfigEnvironment = retainConfigEnvironment
            )
            val process = pb.start()
            addSubprocessSession(key = callerKey, process = process)

            process.inputStream.bufferedReader().forEachLine { string ->
                Logger.debug("[CMDR] Subprocess output received: $string")
                lb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] Subprocess error received: $string")
                lb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = process.waitFor()
            outputPublisher.onComplete()
            val s = "Subprocess exited with code: $exitVal"
            Logger.debug(s)
            lb.appendLine("\n${Calendar.getInstance().time}: $s")
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            Logger.error(s)
            lb.appendLine("\n${Calendar.getInstance().time}: $s")
            outputPublisher.onError(e)
        } finally {
            Logger.logToFile(
                text = lb.toString(),
                args = args,
                directory = workingDir,
                commandId = "N/A",
                name = commandPath
            )
            endSession(callerKey)
        }
    }

    /**
     * Spawns an asynchronous, interactive subprocess that include Rx input
     * publishing routes for sending data to the subprocess during its
     * runtime, while being bound to the coroutine scope of the caller
     *
     * @param executable subprocess command object
     * @param userArgs command arguments
     * @param inputPublisher input route for subprocess STDIN
     * @param outputPublisher output route for subprocess STDOUT
     * @param receiverScope coroutine scope of caller
     */
    fun executeInteractive(
        executable: ISubprocess,
        userArgs: List<String>,
        env: MutableMap<String, String> = mutableMapOf(),
        inputPublisher: BehaviorProcessor<String>,
        outputPublisher: BehaviorProcessor<String>,
        receiverScope: CoroutineScope = GlobalScope,
        retainConfigEnvironment: Boolean = true
    ) {
        val lb = StringBuilder()
        val args = buildArguments(executable, userArgs)
        val wdp = getWorkingDirectoryPath(executable)
        try {
            val pb = buildProcess(
                workingDir = wdp,
                args = args,
                env = env,
                retainConfigEnvironment = retainConfigEnvironment
            )
            val process = pb.start()
            addSubprocessSession(executable.callerKey, process)

            // background job for accepting input and writing to subproc STDIN
            receiverScope.launch {
                inputPublisher.subscribe { sig ->
                    if (sig == "SIGKILL") {
                        process.outputStream.close()
                        process.inputStream.close()
                        process.errorStream.close()
                        process.destroyForcibly()
                    }
                    Logger.debug("Input queued: $sig")
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
                    lb.appendLine(s)
                    outputPublisher.onNext(string)
                }
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] CGI process error received: $string")
                lb.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = process.waitFor()
            val s = "Subprocess exited with code: $exitVal"
            Logger.debug(s)
            lb.appendLine("\n${Calendar.getInstance().time}: $s")
            outputPublisher.onComplete()
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            Logger.error(s)
            lb.appendLine("\n${Calendar.getInstance().time}: $s")
        } finally {
            val name = getProgramName(executable)
            Logger.logToFile(
                lb.toString(),
                userArgs,
                wdp,
                executable.callerKey.toString(),
                name
            )
            endSession(executable.callerKey)
        }
    }

    /**
     * Identical behavior to [executeInteractive], except this overload allows
     * the caller to specify all elements of a subprocess/command as opposed to
     * relying on the configuration files
     *
     * @param programPath path to command, ex. /bin/python
     * @param args command arguments, with alias at index 0 if needed
     * @param workingDir working directory from which to execute command
     * @param callerKey caller ID key
     * @param env any environment variables to be added to [ProcessBuilder]
     * @param inputPublisher input route for subprocess STDIN
     * @param outputPublisher output route for subprocess STDOUT
     * @param receiverScope coroutine scope of caller
     * @param retainConfigEnvironment retain all env variables from config
     */
    fun executeExplicitInteractive(
        programPath: String,
        args: List<String>,
        workingDir: String,
        callerKey: String,
        env: MutableMap<String, String> = mutableMapOf(),
        inputPublisher: BehaviorProcessor<String>,
        outputPublisher: BehaviorProcessor<String>,
        receiverScope: CoroutineScope = GlobalScope,
        retainConfigEnvironment: Boolean = true
    ) {
        val logBuilder = StringBuilder()
        try {
            val cmdArgList = with(arrayListOf<String>()) {
                add(programPath)
                addAll(args)
                this
            }

            val pb = buildProcess(
                workingDir = workingDir,
                args = cmdArgList,
                env = env,
                retainConfigEnvironment = retainConfigEnvironment
            )
            val process = pb.start()
            addSubprocessSession(callerKey, process)

            // background job for accepting input and writing to subproc STDIN
            receiverScope.launch {
                inputPublisher.subscribe { sig ->
                    if (sig == "SIGKILL") {
                        process.outputStream.close()
                        process.inputStream.close()
                        process.errorStream.close()
                        process.destroyForcibly()
                    }
                    Logger.debug("Input queued: $sig")
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
                    logBuilder.appendLine(s)
                    outputPublisher.onNext(string)
                }
            }

            process.errorStream.bufferedReader().forEachLine { string ->
                Logger.error("[CMDR] CGI process error received: $string")
                logBuilder.appendLine("${Calendar.getInstance().time}: $string")
                outputPublisher.onNext(string)
            }

            val exitVal = process.waitFor()
            val s = "Subprocess exited with code: $exitVal"
            Logger.debug(s)
            logBuilder.appendLine("\n${Calendar.getInstance().time}: $s")

            outputPublisher.onComplete()
        } catch (e: Exception) {
            val s = "Subprocess exited with error: ${e.localizedMessage}"
            Logger.error(s)
            logBuilder.appendLine("\n${Calendar.getInstance().time}: $s")
        } finally {
            Logger.logToFile(
                logBuilder.toString(),
                args = args,
                commandId = "N/A",
                name = programPath,
                directory = workingDir
            )
            endSession(callerKey)
        }
    }

    /**
     * Spawns a file watcher at the given directory, and notifies when files are created, modified, or deleted; will
     * also echo the contents, if requested
     *
     * @param watchDirectory directory to watch for changes
     * @param outputPublisher Rx publisher for output to caller
     * @param killChannel coroutines channel for killing the watcher process, when needed
     * @param receiverScope coroutine scope given by receiver, if needed; defaults to [GlobalScope]
     * @param echoContents whether to echo the contents of the file to the caller
     */
    fun spawnFileWatch(
        watchDirectory: String,
        outputPublisher: BehaviorProcessor<String>,
        killChannel: Channel<Int>,
        receiverScope: CoroutineScope = GlobalScope,
        echoContents: Boolean = true
    ) {
        val logBuilder = StringBuilder()
        try {
            val watchService = FileSystems.getDefault().newWatchService()
            val watchPath = File(watchDirectory).toPath()
            val keys = watchPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            val watchKey = watchService.take()
            var hold = true
            receiverScope.launch {
                // job to watch caller's channel for kill signal
                val inputJob = async {
                    Logger.debug("File watch kill channel launched...")
                    killChannel.consumeAsFlow().collect {
                        Logger.debug("File watch control channel signal received: $it")
                        if (it.toUByte() == SIG_KILL) {
                            Logger.debug("File watch SIG_KILL received...")
                            hold = false
                        }
                    }
                }
                inputJob.start()
                while (hold) {
                    for (event in watchKey.pollEvents()) {
                        val kind = event.kind()
                        val context = event.context() as Path
                        val fileDir = "$watchDirectory/${context}"
                        val s = StringBuilder()
                        when (kind) {
                            StandardWatchEventKinds.ENTRY_CREATE -> {
                                // TODO: pass lambdas for handling events here
                                Logger.debug("[CMDR] File created at: $fileDir")
                                if (echoContents) {
                                    val f = File(fileDir)
                                    logBuilder.appendLine("New file contents ($fileDir):")
                                    f.reader().forEachLine { str ->
                                        logBuilder.appendLine(str)
                                        s.appendLine(str)
                                    }
                                    outputPublisher.onNext(s.toString())
                                }
                            }
                            StandardWatchEventKinds.ENTRY_DELETE -> {
                                // TODO: pass lambdas for handling events here
                                Logger.debug("[CMDR] File deleted at: $fileDir")
                            }
                            StandardWatchEventKinds.ENTRY_MODIFY -> {
                                // TODO: pass lambdas for handling events here
                                Logger.debug("[CMDR] File modified at: $fileDir")
                            }
                            else -> Logger.debug("Unhandled event: $kind - $context")
                        }
                    }
                    watchKey.reset()
                }
                inputJob.cancel()
                watchKey.cancel()
                watchService.close()
                keys.cancel()
                outputPublisher.onComplete()
                Logger.logToFile(
                    text = logBuilder.toString(),
                    args = listOf(),
                    directory = watchDirectory,
                    commandId = "N/A",
                    name = "File watcher"
                )
            }
        } catch (e: Exception) {
            val msg = "File watcher exited with error: ${e.localizedMessage}"
            val c = Calendar.getInstance().time
            Logger.error(msg)
            logBuilder.appendLine("\n${c}: $msg")
            outputPublisher.onError(e)
        }
    }

    fun endSession(callerKey: String, forcible: Boolean = false) {
        try {
            procMap[callerKey]?.let { procs ->
                procs.forEach {
                    Logger.debug("Terminating subprocess with ID: ${it.pid()}")
                    if (forcible) it.destroyForcibly()
                    else it.destroy()
                }
                procMap.remove(callerKey)
                Logger.debug("Session ended for caller: $callerKey")
            } ?: kotlin.run {
                Logger.error("Error ending session for caller $callerKey: session not found")
            }
        } catch (e: Exception) { Logger.error(e) }
    }

    /**
     * Forcibly destroys any laggard processes; not intended for graceful
     * termination, which should be handled by [endSession]
     */
    fun killAll() {
        procMap.forEach { endSession(callerKey = it.key, forcible = true) }
        procMap.clear()
    }

    //endregion

    //region Private Class Functions

    /**
     * Takes a path code and retrieves a command path from the config values
     *
     * @param code path code for the given command
     * @return path of program to be executed per [IProgram.code]
     */
    @Throws
    private fun getCommandPath(code: Int): String =
        config.paths.values
            .first { it.code == code }
            .path

    /**
     * Retrieves the program name from the [IngenConfig] path map
     *
     * @param ex subprocess object to reference command path from
     * @return program name string, or null on failure
     */
    @Throws
    private fun getProgramName(ex: ISubprocess) =
        config
            .paths
            .entries
            .first { it.value.code == ex.command.programCode }
            .key

    /**
     * Gets the working directory [File] object of the given command by
     * appending the nested directory path to the runtime directory's path
     * string
     *
     * @param ex subprocess command used to compound a working directory
     * @return runtime execution path needed by a given subprocess
     */
    @Throws
    private fun getWorkingDirectoryPath(ex: ISubprocess) : String {
        return with(arrayListOf<String>()) {
            if (ex.command.directory.isBlank())
                add("${config.runtimeDirectory}/")
            else add("${ex.command.directory}/")
            val sb = StringBuilder()
            forEach {  s -> sb.append(s) }
            val s = sb.toString()
            Logger.debug("Subprocess working directory: $s")
            s
        }
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
    @Throws(
        IOException::class,
        FileNotFoundException::class,
        FileSystemException::class
    )
    private fun buildProcess(
        workingDir: String,
        args: List<String>,
        env: MutableMap<String, String>,
        retainConfigEnvironment: Boolean = true,
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

            if (retainConfigEnvironment)
                config.envVar
                    .filter { env.containsKey(it.key).not() }
                    .forEach { v -> env[v.key] = v.value }

            for (i in env)
                environment()[i.key] = i.value
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
        val path = getCommandPath(ex.command.programCode)
        return with(arrayListOf<String>()) {
            add(path)
            addAll(argsList)
            this.filter { it.isNotBlank() }
        }
    }

    private fun addSubprocessSession(key: String, process: Process) {
        procMap[key]?.let { procs ->
            if (procs.contains(process).not()) {
                procs.add(process)
                Logger.debug("New process added to caller $key: ${process.pid()}")
            } else Logger.debug("Ignoring duplicate process for caller $key: ${process.pid()}")
        } ?: kotlin.run {
            procMap.put(key, arrayListOf(process))
            Logger.debug("New session created for caller: $key " +
                    "\n\tPID: (${process.pid()})")
        }
    }

    //endregion
}
