@file:OptIn(InternalCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.InternalCoroutinesApi

/**
 * Contract for all service implementations managing callers and their requested subprocess
 * executions; in the simplest of circumstances with a single kiosked app leveraging this lib,
 * there will only be a single caller using the singleton service; in more complex IoT-centric
 * applications, this service can manage the execution of subprocesses from multiple callers
 * from, for example, a websocket connection with the server running an app embedded with Ingen;
 * in this latter situation, there are potentially "1 to 1 or many" possible combinations of
 * callers, supported correlatively by the maximum throughput of the server
 *
 * @property config configuration object for Ingen runtime
 * @property commands list of commands parsed from the JSON commands file
 * @property commander commander object for native subprocess execution
 * @property processorMap map of all callers and their corresponding publishers
 */
interface ICommandService {
    val config: IngenConfig
    val commands: List<ISubprocess>
    val commander: Commander
    val processorMap: MutableMap<String, ArrayList<BehaviorProcessor<String>>>

    /**
     * Simplest method for executing a simple, blocking FIFO call; meant for the simplest
     * and most lightweight subprocess calls
     *
     * @param key unique caller ID key, for both cyphering data and validating subprocess calls
     * @param code subprocess uid, corresponding to an entry within the JSON commands file
     * @param args list of user-supplied arguments to accompany the subprocess call
     * @param environmentVars any required supplemental environment vars
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     * @return subprocess output as a raw string, via STDOUT
     *
     */
    fun execute(
        key: String,
        code: Int,
        args: List<String> = listOf(),
        environmentVars: Map<String, String> = mapOf(),
        retainConfigEnv: Boolean = true
    ): String

    /**
     * Method for executing asynchronous subprocesses that likely carry long-running or more complex
     * operations than those launched via [execute]
     * @param key unique caller ID key, for both cyphering data and validating subprocess calls
     * @param code subprocess uid, corresponding to an entry within the JSON commands file
     * @param args list of user-supplied arguments to accompany the subprocess call
     * @param threaded whether to explicitly thread the subscription to the Rx publisher
     * @param environmentVars any required supplemental environment vars
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     */
    fun executeAsync(
        key: String,
        code: Int,
        args: List<String> = listOf(),
        threaded: Boolean = false,
        environmentVars: Map<String, String> = mapOf(),
        retainConfigEnv: Boolean = true
    )

    /**
     * Simplest method for executing an explicit, blocking FIFO call; meant for the simplest
     * and most lightweight subprocess calls; using this method implies you are calling a
     * program on the native system that is NOT listed within the JSON commands file
     *
     * @param key unique caller ID key, for both cyphering data and validating subprocess calls
     * @param path explicit path to program
     * @param workingDir directory path from which to launch the native program
     * @param args list of user-supplied arguments to accompany the subprocess call
     * @param environmentVars any required supplemental environment vars
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     * @return subprocess output as a raw string, via STDOUT
     *
     */
    fun executeExplicit(
        key: String,
        path: String,
        workingDir: String,
        args: List<String> = listOf(),
        environmentVars: Map<String, String> = mapOf(),
        retainConfigEnv: Boolean = true
    ): String

    /**
     * Method for executing asynchronous subprocesses that are NOT contained within the JSON commands file
     * @param key unique caller ID key, for both cyphering data and validating subprocess calls
     * @param path explicit path to program
     * @param workingDir directory path from which to launch the native program
     * @param args list of user-supplied arguments to accompany the subprocess call
     * @param threaded whether to explicitly thread the subscription to the Rx publisher
     * @param environmentVars any required supplemental environment vars
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     */
    fun executeExplicitAsync(
        key: String,
        path: String,
        workingDir: String,
        args: List<String> = listOf(),
        threaded: Boolean = false,
        environmentVars: Map<String, String> = mapOf(),
        retainConfigEnv: Boolean = true
    )

    /**
     * Spawns a file watcher subprocess, watching a given directory
     *
     * @param directory path to directory to watch
     * @param recursive whether to recursively watch the given directory's children
     */
    fun watchFiles(directory: String, recursive: Boolean = true)

    /**
     * Destroys a given native program by PID
     *
     * @param pid the Linux "PID" of the program in question
     */
    fun destroy(pid: String)

    /**
     * Destroys all running programs for a given caller by caller key
     *
     * @param key unique caller ID key
     * @param forcible whether to forcibly destroy the given programs
     */
    fun destroyForCaller(key: String, forcible: Boolean = false)

    /**
     * Disposes all [BehaviorProcessor] objects by triggering their onComplete()
     * class method, all by caller key
     *
     * @param key unique caller ID key
     */
    fun disposeForCaller(key: String)

    /**
     * Ends a caller session by key, meaning it destroys all running programs belonging
     * to the caller and also disposes (gracefully) of any active [BehaviorProcessor]
     * objects linked to the caller
     *
     * @param key unique caller ID key
     * @param forcible whether to forcibly destroy the programs belonging to the given caller
     */
    fun endCallerSession(key: String, forcible: Boolean = false)

    /**
     * Destroys all running programs belonging to all callers who have running subprocesses
     */
    fun batchDestroy()

    /**
     * Gracefully disposes of all active [BehaviorProcessor] objects belonging to all callers
     */
    fun batchDispose()

    /**
     * All-in-one teardown method meant for cleaning up runtime prior to app termination
     * (or any other such situations); the most important part is the program cleanup
     *
     * @param postRun lamba for any other logic that needs to be run after the cleanup process
     */
    fun teardown(postRun: (() -> Unit)? = null)
}

/**
 * Implementation of [ICommandService]
 */
object CommandService : ICommandService {
    override val config: IngenConfig = ConfigBuilder.buildConfig()
    override val commands: List<ISubprocess> =
        ConfigBuilder.buildSubprocessCalls() ?: listOf()
    // TODO: pass a config property, not the whole thing
    override val commander: Commander =
        Commander(configuration = ConfigBuilder.buildConfig())
    override val processorMap: MutableMap<String, ArrayList<BehaviorProcessor<String>>> =
        mutableMapOf()

    override fun execute(
        key: String,
        code: Int,
        args: List<String>,
        environmentVars: Map<String, String>,
        retainConfigEnv: Boolean
    ): String {
        val sp = commands.first { it.uid.toInt() == code } as Subprocess
        return commander.execute(
            callerKey = key,
            executable = sp,
            userArgs = args
        )
    }

    override fun executeAsync(
        key: String,
        code: Int,
        args: List<String>,
        threaded: Boolean,
        environmentVars: Map<String, String>,
        retainConfigEnv: Boolean
    ) {
        val sp = commands.first { it.uid.toInt() == code } as Subprocess
        val op = BehaviorProcessor.create<String>()
        processorMap[key]?.add(op) ?: kotlin.run {
            processorMap.put(key, arrayListOf(op))
        }

    }

    override fun executeExplicit(
        key: String,
        path: String,
        workingDir: String,
        args: List<String>,
        environmentVars: Map<String, String>,
        retainConfigEnv: Boolean
    ): String {
        return commander.executeExplicit(
            callerKey = key,
            commandPath = path,
            args = args,
            env = environmentVars,
            workingDir = workingDir,

        )
    }

    override fun executeExplicitAsync(
        key: String,
        path: String,
        workingDir: String,
        args: List<String>,
        threaded: Boolean,
        environmentVars: Map<String, String>,
        retainConfigEnv: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun watchFiles(directory: String, recursive: Boolean) {
        TODO("Not yet implemented")
    }

    override fun destroy(pid: String) {
        TODO("Not yet implemented")
    }

    override fun batchDestroy() {
        commander.killAll()
    }

    override fun destroyForCaller(key: String, forcible: Boolean) {
        commander.endSession(callerKey = key, forcible = forcible)
    }

    override fun disposeForCaller(key: String) {
        processorMap[key]?.let { it.forEach { bp -> bp.onComplete() } }
            ?: kotlin.run { Logger.error("Unable to retrieve disposable map for caller: $key") }
    }

    override fun endCallerSession(key: String, forcible: Boolean) {
        destroyForCaller(key = key, forcible = forcible)
        disposeForCaller(key = key)
        processorMap.remove(key)
    }

    override fun batchDispose() {
        processorMap.forEach { it.value.forEach { cd -> cd.onComplete() } }
        processorMap.clear()
    }

    override fun teardown(postRun: (() -> Unit)?) {
        batchDestroy()
        batchDispose()
        postRun?.invoke()
    }
}