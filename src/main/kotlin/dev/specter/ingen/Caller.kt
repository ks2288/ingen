package dev.specter.ingen

import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.*

/**
 * Contract for all callers implementing this library, intending to make use of long-running, asynchronous
 * "service"-oriented subprocesses; properties with getters are not required but for their correlating use cases;
 * for example, if you need an input processor for an interactive service, simply override it with a type;
 * conversely, if you do not need one, simply do not override it within your concrete implementation; properties
 *  typed as wildcard and/or [Any] MUST have overrides to disambiguate "return" types
 *
 * @property tag string tag for logging purposes
 * @property key unique caller UID
 * @property compositeDisposable composite "bucket" for efficient subscription management/termination
 * @property dataPublisher outermost publisher meant to be consumed by all relevant callers; override specifies type
 * @property bpStrategy Rx backpressure strategy for subscriptions; defaults to [BackpressureStrategy.LATEST]
 * @property inputProcessor optional Rx processor that exposes an interactive input channel; override only if needed
 * @property launchArgs arguments to include with native command execution
 * @property dir working directory from which command should operate
 * @property env map of environment variables to be added to execution env, if needed
 * @property spKey subprocess key corresponding to config Ingen file, optional in place of following
 * @property path program path to call, optional in place of the preceding
 */
interface IPeripheralService {
    val tag: String
    val key: String
    val compositeDisposable: CompositeDisposable
    val dataPublisher: BehaviorProcessor<Any>
    val bpStrategy: BackpressureStrategy
        get() = BackpressureStrategy.LATEST
    val inputProcessor: BehaviorProcessor<*>?
        get() = null
    val launchArgs: List<String>
        get() = listOf()
    val dir: String
        get() = IngenConfig.INGEN_DEFAULT_DIR
    val env: Map<String, String>
        get() = mapOf()
    val spKey: Int?
        get() = null
    val path: String?
        get() = null

    /**
     * Reflector method for correctly parsing string output from a subprocess/service in preparation
     * for publishing via [dataPublisher]
     */
    fun reflect(raw: String): Any?

    /**
     * Starts the service's backing subprocess asynchronously, and prepares the [dataPublisher] for
     * consumption by an arbitrary number of subscribers; by default, operations will execute on the
     * [GlobalScope] in order to support "daemon"-like operation
     *
     * @param scope coroutine scope within which execution should take place; defaults to [GlobalScope]
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun start(scope: CoroutineScope = GlobalScope) {
        val op = BehaviorProcessor.create<String>()
        compositeDisposable.add(
            op.toObservable()
                .toFlowable(bpStrategy)
                .subscribeBy(
                    onNext = {
                        reflect(raw = it as String)?.let { data -> dataPublisher.onNext(data) }
                    },
                    onError = { Logger.error(it) },
                    onComplete = { Logger.debug("SensorService onComplete() triggered...") }
                )
        )
        val req = with(ILaunchRequest) {
            spKey?.let { k ->
                create(
                    key = key,
                    uid = k,
                    args = launchArgs,
                    env = env
                )
            } ?: path?.let { p ->
                ILaunchRequest.create(
                    key = key,
                    path = p,
                    directory = dir,
                    args = launchArgs,
                    env = env
                )
            }
        } ?: kotlin.run {
            Logger.error("Unable to create launch request for caller: $key" +
                    "\n\tsubprocess UID and/or explicit path missing from $tag")
            op.onComplete()
            return
        }
        scope.launch {
            Dispatcher.executeAsync(
                request = req,
                ioRoute = IORoute(op, null),
                scope = this
            )
        }
    }

    /**
     * Stops the subprocess backing the service, and also allows for the execution of any necessary
     * post-run logic required by the caller
     *
     * @param postRun lambda for any necessary logic required after stopping the service
     */
    fun stop(postRun: (() -> Unit)? = null) {
        compositeDisposable.dispose()
        Dispatcher.disposeForCaller(key = key)
        postRun?.invoke()
    }
}