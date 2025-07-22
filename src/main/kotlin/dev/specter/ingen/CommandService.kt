@file:OptIn(InternalCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.*

/**
 * Contract for all service implementations managing callers and their requested subprocess
 * executions; in the simplest of circumstances with a single kiosked app leveraging this lib,
 * there will only be a single caller using the singleton service; in more complex IoT-centric
 * applications, this service can manage the execution of subprocesses from multiple callers
 * from, for example, a websocket connection with the server running an app embedded with Ingen;
 * in this latter situation, there are potentially "1 to 1 or many" possible combinations of
 * callers, supported correlatively by the maximum throughput of the server and its clients
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
    // TODO: change the value to an IORoute to accommodate disposing of input publishers along with output publishers
    val processorMap: MutableMap<String, ArrayList<BehaviorProcessor<String>>>

    /**
     * Simplest method for executing a simple, blocking FIFO call; meant for the simplest
     * and most lightweight subprocess calls
     *
     * @param request execution request containing all relevant information about caller and program
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     * @return subprocess output as a raw string, via STDOUT
     *
     */
    fun execute(
        request: IExecRequest,
        retainConfigEnv: Boolean = true
    ): String

    /**
     * Method for executing asynchronous subprocesses that likely carry long-running or more complex
     * operations than those launched via [execute]
     *
     * @param request execution request containing all relevant information about caller and program
     * @param ioRoute Rx processor pair for subprocess IO
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     * @param scope caller's coroutine scope, if provided (defaults to [GlobalScope])
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun executeAsync(
        request: IExecRequest,
        ioRoute: IORoute,
        retainConfigEnv: Boolean = true,
        scope: CoroutineScope = GlobalScope
    )

    /**
     * Simplest method for executing an explicit, blocking FIFO call; meant for the simplest
     * and most lightweight subprocess calls; using this method implies you are calling a
     * program on the native system that is NOT listed within the JSON commands file
     *
     * @param request execution request containing all relevant information about caller and program
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     * @return subprocess output as a raw string, via STDOUT
     *
     */
    fun executeExplicit(
        request: IExecRequestExplicit,
        retainConfigEnv: Boolean = true
    ): String

    /**
     * Method for executing asynchronous subprocesses that are NOT contained within the JSON commands file
     *
     * @param request execution request containing all relevant information about caller and program
     * @param ioRoute Rx processor pair for subprocess IO
     * @param retainConfigEnv whether to retain the environment variables listed in the config file
     * @param scope caller's coroutine scope, if provided (defaults to [GlobalScope])
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun executeExplicitAsync(
        request: IExecRequestExplicit,
        ioRoute: IORoute,
        retainConfigEnv: Boolean = true,
        scope: CoroutineScope = GlobalScope
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
     * (or any other such situations); the most important part is the program cleanup;
     * performs three main steps:
     *  1. destroys all native programs and catalogs the PIDs
     *  2. "disposes" of the behavior processors' subscriptions by triggering their onComplete()
     *  3. executes any given logic via lamba, specific to the application implementing this library
     *
     * @param postRun lamba for any other logic that needs to be run after the cleanup process
     */
    fun teardown(postRun: (() -> Unit)? = null)
}

/**
 * Implementation of [ICommandService] to be used as a singleton within any application that implements
 * this library; can be tested on its own (as done within this library code) or mocked via its parent \
 * interface to support complex unit testing untethered from a native Linux system
 */
object CommandService : ICommandService {
    override val config: IngenConfig = ConfigBuilder.buildConfig()
    override val commands: List<ISubprocess> =
        ConfigBuilder.buildSubprocesses() ?: listOf()
    // TODO: pass a config property, not the whole thing
    override val commander: Commander =
        Commander(configuration = ConfigBuilder.buildConfig())
    override val processorMap: MutableMap<String, ArrayList<BehaviorProcessor<String>>> =
        mutableMapOf()

    override fun execute(
        request: IExecRequest,
        retainConfigEnv: Boolean
    ): String {
        val sp = commands.first { it.uid.toInt() == request.subprocessUID } as Subprocess
        return commander.execute(
            callerKey = request.callerKey,
            executable = sp,
            userArgs = request.userArgs,
            env = request.envVars,
            retainConfigEnvironment = retainConfigEnv
        )
    }

    override suspend fun executeAsync(
        request: IExecRequest,
        ioRoute: IORoute,
        retainConfigEnv: Boolean,
        scope: CoroutineScope
    ) {
        var job: Deferred<Unit>? = null
        try {
            val sp = commands.first { it.uid.toInt() == request.subprocessUID } as Subprocess
            processorMap[request.callerKey]?.add(ioRoute.first) ?: kotlin.run {
                processorMap.put(request.callerKey, arrayListOf(ioRoute.first))
            }
            job = scope.async {
                ioRoute.second?.let {
                    commander.executeInteractive(
                        callerKey = request.callerKey,
                        executable = sp,
                        userArgs = request.userArgs,
                        env = request.envVars,
                        inputPublisher = it,
                        outputPublisher = ioRoute.first,
                        receiverScope = scope,
                        retainConfigEnvironment = retainConfigEnv
                    )
                } ?: kotlin.run {
                    commander.executeRx(
                        callerKey = request.callerKey,
                        executable = sp,
                        userArgs = request.userArgs,
                        env = request.envVars,
                        outputPublisher = ioRoute.first,
                        retainConfigEnvironment = retainConfigEnv
                    )
                }
            }
            job.start()
            job.await()
        } catch (e: Exception) {
            Logger.error("Error executing async subprocess with code ${request.subprocessUID}: ${e.localizedMessage}")
            job?.cancel("Async coroutines job failed with key: ${job.key}", e)
            processorMap[request.callerKey]?.remove(ioRoute.first)
        }
    }

    override fun executeExplicit(
        request: IExecRequestExplicit,
        retainConfigEnv: Boolean
    ): String {
        return commander.executeExplicit(
            callerKey = request.callerKey,
            commandPath = request.programPath,
            args = request.userArgs,
            env = request.envVars,
            workingDir = request.workingDir,
            retainConfigEnvironment = retainConfigEnv
        )
    }

    override suspend fun executeExplicitAsync(
        request: IExecRequestExplicit,
        ioRoute: IORoute,
        retainConfigEnv: Boolean,
        scope: CoroutineScope
    ) {
        var job: Deferred<Unit>? = null
        try {
            processorMap[request.callerKey]?.add(ioRoute.first) ?: kotlin.run {
                processorMap.put(request.callerKey, arrayListOf(ioRoute.first))
            }
            job = scope.async {
                ioRoute.second?.let {
                    commander.executeExplicitInteractive(
                        callerKey = request.callerKey,
                        programPath = request.programPath,
                        args = request.userArgs,
                        workingDir = request.workingDir,
                        env = request.envVars,
                        inputPublisher = it,
                        outputPublisher = ioRoute.first,
                        receiverScope = scope,
                        retainConfigEnvironment = retainConfigEnv
                    )
                } ?: kotlin.run {
                    commander.executeExplicitRx(
                        callerKey = request.callerKey,
                        programPath = request.programPath,
                        args = request.userArgs,
                        env = request.envVars,
                        workingDir = request.workingDir,
                        outputPublisher = ioRoute.first,
                        retainConfigEnvironment = retainConfigEnv
                    )
                }
            }
            job.start()
            job.await()
        } catch (e: Exception) {
            Logger.error("Error executing async subprocess with program path ${request.programPath}: ${e.localizedMessage}")
            job?.cancel("Async coroutines job failed with key: ${job.key}", e)
            processorMap[request.callerKey]?.remove(ioRoute.first)
        }
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