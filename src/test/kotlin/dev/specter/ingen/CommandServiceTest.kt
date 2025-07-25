@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.CommanderTest.Companion.PYTHON_PATH
import dev.specter.ingen.FileOpsTest.Companion.TEARDOWN_DELAY
import dev.specter.ingen.FileOpsTest.Companion.TEST_FILE_WRITER_PATH
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.CommandConstants
import dev.specter.ingen.util.Logger
import dev.specter.ingen.util.TestConstants
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

/**
 * Refer to this test for understanding the usage within the context of an application that
 * implements this library; each of the core functionalities of Ingen is tested here
 */
class CommandServiceTest {
    private lateinit var composite: CompositeDisposable

    @Before
    fun setup() {
        composite = CompositeDisposable()
    }

    @After
    fun teardown() {
        CommandService.teardown { composite.dispose() }
    }

    /**
     * This represents the core usage for async ops, carrying the following cadence:
     *  1. create IO routes from within caller (such as a view model or its children objects etc.)
     *  2. subscribe to output route, using a composite disposable "bucket"
     *  3. create request using appropriate static interface method
     *  4. execute async (suspend) function within appropriate coroutine scope (i.e. Composable `LaunchedEffect`)
     *  5. await completion and/or handle errors, where appropriate (i.e. behavioral architectures)
     *
     * NOTE: the input processor need not be threaded when executing interactive
     * subprocesses, as is demonstrated (threading) within the [CommanderTest]
     * suite; it can just as easily be wrapped within an async coroutines job to
     * produce the same effect, and with potentially less unexpected behavior than
     * can come with using explicit virtual threads
     */
    @Test
    fun test_service_execute_async() {
        val out = arrayListOf<String>()
        val route = IORoute(BehaviorProcessor.create(), null)
        val request = IExecRequest.create(key = "01010", uid = 2)
        composite.add(
            route.first.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribeBy(
                    onNext = {
                        println("*** Test I/O: $it ***")
                        out.add(it)
                    },
                    onError = {
                        fail("Flowable test error: ${it.localizedMessage}")
                    },
                    onComplete = {}
                )
        )
        val job = GlobalScope.async {
            CommandService.executeAsync(request = request, ioRoute = route)
        }

        runTest {
            job.start()
            job.await()
            assert(out.size == CommanderTest.EXPECTED_ASYNC_OUTPUT_SIZE)
        }
    }

    @Test
    fun test_service_execute_async_explicit() {
        val out = arrayListOf<String>()
        val route = IORoute(BehaviorProcessor.create(), null)
        val request = IExecRequestExplicit.create(
            key = "01011",
            path = PYTHON_PATH,
            directory = IngenConfig.INGEN_DEFAULT_DIR,
            args = listOf(CommanderTest.EXPLICIT_RX_SCRIPT_PATH2)
        )

        composite.add(
            route.first.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribeBy(
                    onNext = {
                        println("*** Test I/O: $it ***")
                        out.add(it)
                    },
                    onError = {
                        fail("Flowable test error: ${it.localizedMessage}")
                    },
                    onComplete = {}
                )
        )

        val job = GlobalScope.async {
            CommandService.executeExplicitAsync(request = request, ioRoute = route)
        }

        runTest {
            job.start()
            job.await()
            assert(out.size == CommanderTest.EXPECTED_EXPLICIT_RX_RESULTS_SIZE)
        }
    }

    @Test
    fun test_service_execute_interactive() {
        val out = arrayListOf<String>()
        val route = IORoute(BehaviorProcessor.create(), BehaviorProcessor.create())
        val request = IExecRequest.create(key = "01012", uid = 1)

        composite.add(
            route.first.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribeBy(
                    onNext = {
                        println("*** Test I/O: $it ***")
                        out.add(it)
                    },
                    onError = {
                        fail("Flowable test error: ${it.localizedMessage}")
                    },
                    onComplete = {}
                )
        )

        val job2 = GlobalScope.async {
            delay(3000)
            route.second!!.onNext("test1")
            delay(1000)
            route.second!!.onNext("test2")
            delay(1000)
            route.second!!.onNext("xx")
        }

        val job = GlobalScope.async {
            CommandService.executeAsync(request = request, ioRoute = route)
        }

        runTest {
            job.start()
            job2.start()
            job2.await()
            job.await()
            assert(out.isNotEmpty())
        }
    }

    @Test
    fun test_service_execute_interactive_explicit() {
        val out = arrayListOf<String>()
        val route = IORoute(BehaviorProcessor.create(), BehaviorProcessor.create())
        val request = IExecRequestExplicit.create(
            key = "01012",
            path = PYTHON_PATH,
            directory = IngenConfig.INGEN_DEFAULT_DIR,
            args = listOf(CommanderTest.INTERACTIVE_MODULE_PATH)
        )

        composite.add(
            route.first.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribeBy(
                    onNext = {
                        println("*** Test I/O: $it ***")
                        out.add(it)
                    },
                    onError = {
                        fail("Flowable test error: ${it.localizedMessage}")
                    },
                    onComplete = {}
                )
        )

        val job2 = GlobalScope.async {
            delay(3000)
            route.second!!.onNext("test1")
            delay(1000)
            route.second!!.onNext("test2")
            delay(1000)
            route.second!!.onNext("xx")
        }

        val job = GlobalScope.async {
            CommandService.executeExplicitAsync(request = request, ioRoute = route)
        }

        runTest {
            job.start()
            job2.start()
            job2.await()
            job.await()
            assert(out.isNotEmpty())
        }
    }

    @Test
    fun test_service_launch_file_watcher() {
        val route = IORoute(BehaviorProcessor.create(), BehaviorProcessor.create())
        val route2 = IORoute(BehaviorProcessor.create(), null)
        val wd = "${TestConstants.TEST_MODULE_DIR}/fw"
        val request = IFileWatchRequest.create(key = "10102", directory = wd)
        val request2 = IExecRequestExplicit.create(
            key = "101110",
            path = PYTHON_PATH,
            directory = IngenConfig.INGEN_DEFAULT_DIR,
            args = listOf(TEST_FILE_WRITER_PATH, "${TestConstants.TEST_MODULE_DIR}/fw")
        )
        val out = arrayListOf<String>()

        composite.add(
            route.first.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(Schedulers.io())
                .subscribeBy(
                    onNext = {
                        Logger.debug("Route 1 output received:\n\n$it")
                        out.add(it)
                    },
                    onError = { fail("Route 1 FW test error: ${it.localizedMessage}") },
                    onComplete = { return@subscribeBy }
                )
        )

        composite.add(
            route2.first.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(Schedulers.io())
                .subscribeBy(
                    onNext = {
                        Logger.debug("Route 2 output received:\n\n$it")
                        out.add(it)
                    },
                    onError = { fail("Route 2 FW test error: ${it.localizedMessage}") },
                    onComplete = { return@subscribeBy }
                )
        )

        GlobalScope.launch {
            CommandService.watchFiles(request = request, route = route, scope = this)
        }

        val job2 = GlobalScope.async {
            CommandService.executeExplicitAsync(request = request2, ioRoute = route2, scope = this)
        }

        runTest {
            job2.start()
            job2.await()
            assert(out.isNotEmpty())
            route.second!!.onNext(CommandConstants.SIG_KILL.toInt().toString())
            delay(TEARDOWN_DELAY)
        }
    }
}