@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.util.Logger
import dev.specter.ingen.util.MockInteractiveService
import dev.specter.ingen.util.MockPeripheralService
import dev.specter.ingen.util.MockServiceData
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

class PeripheralServiceTest {
    private lateinit var composite: CompositeDisposable
    @Before
    fun setup() {
        composite = CompositeDisposable()
    }

    @After
    fun teardown() {
        Dispatcher.teardown()
    }

    @Test
    fun test_peripheral_service_launch() {
        MockPeripheralService.start()
        val out = arrayListOf<MockServiceData>()
        composite.add(
            MockPeripheralService.dataPublisher.toObservable()
                .toFlowable(BackpressureStrategy.LATEST)
                .subscribeBy(
                    onNext = {
                        out.add(it as MockServiceData)
                    },
                    onError = {
                        fail("peripheral service test error: ${it.localizedMessage}")
                    },
                    onComplete = {
                        Logger.debug("peripheral service onComplete() triggered...")
                    }
                )
        )
        runBlocking {
            delay(5000)
            assert(out.isNotEmpty())
            MockPeripheralService.stop()
        }
    }

    // See comments below for implementation/usage guidance
    @Test
    fun test_interactive_service_launch() {
        val out = arrayListOf<String>()
        composite.add(
            // this would be handled in something like a view model class or "service handler" object
            MockInteractiveService.dataPublisher.toObservable()
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribeBy(
                    onNext = {
                        out.add(it as String)
                    },
                    onComplete = {
                        Logger.debug("interactive service onComplete() triggered...")
                    },
                    onError = {
                        fail("interactive service test error: ${it.localizedMessage}")
                    }
                )
        )

        // this simply represents some kind of arbitrary user input actions; two inputs, then one more with a known terminator
        val job = GlobalScope.async {
            delay(3000)
            MockInteractiveService.sendInputSignal("test1")
            delay(1000)
            MockInteractiveService.sendInputSignal("test2")
            delay(1000)
            MockInteractiveService.sendInputSignal("xx")
        }

        // this would be called from whichever UI context (or just simply a caller scope if running headless)
        MockInteractiveService.start()

        runTest {
            job.start()
            job.await()
            assert(out.isNotEmpty())
            // should also be called within scope mentioned above OR some kind of lifecycle handler
            MockInteractiveService.stop()
        }
    }

    companion object {

    }
}