@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.IngenConfig
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

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
            path = CommanderTest.PYTHON_PATH,
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
            path = CommanderTest.PYTHON_PATH,
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
}