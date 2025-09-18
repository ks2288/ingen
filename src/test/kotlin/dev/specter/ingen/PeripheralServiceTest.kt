@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.util.Logger
import dev.specter.ingen.util.MockPeripheralService
import dev.specter.ingen.util.MockServiceData
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
        CommandService.teardown()
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
        }
    }

    companion object {

    }
}