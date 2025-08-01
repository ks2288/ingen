@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.CommandConstants.SIG_KILL
import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.*
import kotlin.io.FileSystemException

typealias FileWatcherID = Pair<WatchKey, WatchService>
typealias ProcessMap = MutableMap<String, ArrayList<Process>>
typealias WatcherMap = MutableMap<String, ArrayList<FileWatcherID>>

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
    private val procMap: ProcessMap = mutableMapOf()
    private val watchMap: WatcherMap = mutableMapOf()

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
        callerKey: String,
        executable: ISubprocess,
        userArgs: List<String>,
        env: Map<String, String> = mutableMapOf(),
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
                text = logBuilder.toString(),
                userArgs,
                directory = wdp,
                callerKey = callerKey,
                uid = executable.uid,
                cmdCode = executable.command.programCode,
                name = name
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
        callerKey: String,
        commandPath: String,
        args: List<String>,
        env: Map<String, String> = mutableMapOf(),
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
                callerKey = callerKey,
                uid = "N/A (explicit execution)",
                cmdCode = -1,
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
        callerKey: String,
        executable: ISubprocess,
        userArgs: List<String>,
        env: Map<String, String> = mutableMapOf(),
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
            addSubprocess(callerKey, process)

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
                text = lb.toString(),
                args = userArgs,
                directory = wdp,
                callerKey = callerKey,
                uid = executable.uid,
                cmdCode = -1,
                name = name
            )
            endSession(callerKey)
        }
    }

    /**
     * Executes an explicit async command, meaning it does not come from the loaded config's
     * program/command maps
     *
     * @param programPath full system path to the desired command
     * @param args arguments to accompany the command path
     * @param workingDir directory from which the command should be executed
     * @param callerKey unique caller identification key
     * @param env map of any environment variables to be added to the execution
     * @param outputPublisher Rx publisher through which output is routed
     * @param retainConfigEnvironment flag for config env variable retention
     */
    fun executeExplicitRx(
        callerKey: String,
        programPath: String,
        args: List<String>,
        workingDir: String,
        env: Map<String, String> = mutableMapOf(),
        outputPublisher: BehaviorProcessor<String>,
        retainConfigEnvironment: Boolean = true
    ) {
        val lb = StringBuilder()
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
            addSubprocess(key = callerKey, process = process)

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
                callerKey = callerKey,
                uid = "N/A (explicit execution)",
                cmdCode = -1,
                name = programPath
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
        callerKey: String,
        executable: ISubprocess,
        userArgs: List<String>,
        env: Map<String, String> = mutableMapOf(),
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
            addSubprocess(callerKey, process)

            // background job for accepting input and writing to subproc STDIN
            receiverScope.launch {
                inputPublisher.subscribe { sig ->
                    if (sig == "SIGKILL") {
                        process.outputStream.close()
                        process.inputStream.close()
                        process.errorStream.close()
                        process.destroy()
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
                text = lb.toString(),
                args = userArgs,
                directory = wdp,
                callerKey = callerKey,
                uid = executable.uid,
                cmdCode = -1,
                name = name
            )
            endSession(callerKey)
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
        callerKey: String,
        programPath: String,
        args: List<String>,
        workingDir: String,
        env: Map<String, String> = mutableMapOf(),
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
            addSubprocess(callerKey, process)

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
                text = logBuilder.toString(),
                args = args,
                callerKey = callerKey,
                name = programPath,
                uid = "N/A (explicit execution)",
                cmdCode = -1,
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
        callerKey: String,
        watchDirectory: String,
        outputPublisher: BehaviorProcessor<String>,
        killChannel: BehaviorProcessor<String>,
        receiverScope: CoroutineScope = GlobalScope,
        echoContents: Boolean = true
    ) {
        val logBuilder = StringBuilder()
        var watchService: WatchService? = null
        var keySet: WatchKey? = null
        var watchKey: WatchKey? = null
        try {
            val watchPath = File(watchDirectory).toPath()
            watchService = FileSystems.getDefault().newWatchService()
            keySet = watchPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            watchKey = watchService.take()
            addFileWatcher(
                callerKey = callerKey,
                watchKey = watchKey!!,
                watchService = watchService!!
            )
            var hold = true
            receiverScope.launch {
                // job to watch caller's channel for kill signal
                val inputJob = async {
                    Logger.debug("File watch kill channel launched...")
                    killChannel.toObservable()
                        .toFlowable(BackpressureStrategy.LATEST)
                        .subscribeBy(
                            onNext = {
                                Logger.debug("File watch control channel signal received: $it")
                                if (it.toInt().toUByte() == SIG_KILL) {
                                    Logger.debug("File watch SIG_KILL received...")
                                    hold = false
                                }
                            },
                            onError = {
                                Logger.error("File watch control channel error: ${it.localizedMessage}")
                            }
                        )
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
                keySet.cancel()
                outputPublisher.onComplete()
                Logger.logToFile(
                    text = logBuilder.toString(),
                    args = listOf(),
                    directory = watchDirectory,
                    callerKey = callerKey,
                    uid = "N/A (File Watch Service)",
                    cmdCode = -1,
                    name = "File watcher"
                )
            }
        } catch (e: Exception) {
            watchKey?.cancel()
            watchService?.close()
            keySet?.cancel()
            val msg = "File watcher exited with error: ${e.localizedMessage}"
            val c = Calendar.getInstance().time
            Logger.error(msg)
            logBuilder.appendLine("\n${c}: $msg")
            outputPublisher.onError(e)
        }
    }

    /**
     * Ends all processes executed by a given caller, forcibly if desire
     *
     * @param callerKey unique caller identification key
     * @param forcible flag for forcible process destruction
     */
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
        watchMap.forEach {
            Logger.debug("Ending FW Service for caller: ${it.key}")
            it.value.forEach { id ->
                id.first.cancel()
                Logger.debug("Watch key cancelled: ${id.first}")
                id.second.close()
                // TODO: there has to be a better way to label this termination than the hash code
                Logger.debug("Watch service closed with hash code: ${id.second.hashCode()}")
            }
        }
        watchMap.clear()
    }

    //endregion

    //region Private Class Functions

    /**
     * Takes a path code and retrieves a command path from the config values
     *
     * @param code path code for the given command
     * @return path of program to be executed per configuration file
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
        env: Map<String, String>,
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

            val environment = env.toMutableMap()
            if (retainConfigEnvironment)
                config.envVar
                    .filter { env.containsKey(it.key).not() }
                    .forEach { v -> environment[v.key] = v.value }

            for (i in environment)
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
            filter { it.isNotBlank() }
        }
    }

    /**
     * Adds a new subprocess executed by a given caller to the management map
     *
     * @param key unique caller identification key
     * @param process [Process] object running on the host system
     */
    private fun addSubprocess(key: String, process: Process) {
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

    /**
     * Adds a new file watcher (by key) to the local map, for cataloging/cleanup
     *
     * @param callerKey unique caller UID
     * @param watchKey [WatchService] key, unique to the native file watcher process
     * @param watchService reference to [WatchService]
     */
    private fun addFileWatcher(callerKey: String, watchKey: WatchKey, watchService: WatchService) {
        val new = Pair(watchKey, watchService)
        watchMap[callerKey]?.let { w ->
            w.add(new)
            Logger.debug("New file watcher added for caller $callerKey with watch key: $watchKey")
        } ?: kotlin.run {
            watchMap.put(callerKey, arrayListOf(new))
        }
    }

    //endregion
}
