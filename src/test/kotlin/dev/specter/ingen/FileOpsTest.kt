@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.CommanderTest.Companion.PYTHON_PATH
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.CommandConstants
import dev.specter.ingen.util.Logger
import dev.specter.ingen.util.TestConstants
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.fail

class FileOpsTest {
    private lateinit var commander: Commander
    private lateinit var fwPublisher: BehaviorProcessor<String>
    private lateinit var inputPublisher: BehaviorProcessor<String>

    @Before
    fun setup() {
        commander = Commander()
        fwPublisher = BehaviorProcessor.create()
        inputPublisher = BehaviorProcessor.create()
    }

    @After
    fun teardown() {
        runBlocking {
            GlobalScope.async {
                delay(TEARDOWN_DELAY)
                commander.killAll()
            }.await()
        }
    }

    @Test
    fun test_file_watcher_async() {
        val out = arrayListOf<String>()
        val wd = "${TestConstants.TEST_MODULE_DIR}/fw"
        fwPublisher
            .toObservable()
            .toFlowable(BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.io())
            .subscribeBy(
                onNext = {
                    Logger.debug("File watcher output received:\n\n$it")
                    out.add(it)
                },
                onError = { fail("File watcher test error: ${it.localizedMessage}") },
                onComplete = { return@subscribeBy }
            )
        val ctlProc = BehaviorProcessor.create<String>()

        thread {
            commander.spawnFileWatch(
                callerKey = "01010",
                watchDirectory = wd,
                outputPublisher = fwPublisher,
                killChannel = ctlProc,
            )
        }

        // block main thread and run test scenario with separate subprocess
        runTest {
            commander.executeAsync(
                programPath = PYTHON_PATH,
                args = listOf(TEST_FILE_WRITER_PATH, "${TestConstants.TEST_MODULE_DIR}/fw"),
                workingDir = IngenConfig.INGEN_DEFAULT_DIR,
                callerKey = "101010",
                outputPublisher = fwPublisher
            )
            // allow a bit of time for thread ops to maintain/get to good state
            delay(TEARDOWN_DELAY)
            // assert after delay (currently just checking for callback ability)
            assert(out.isNotEmpty())
            // send the SIG_KILL to the kill channel to break the threaded method's loop
            ctlProc.onNext(CommandConstants.SIG_KILL.toInt().toString())
        }
    }

    companion object {
        val TEST_FILE_WRITER_PATH = "${TestConstants.TEST_RES_DIR}/test_file_writer.py"
        const val TEARDOWN_DELAY = 3000L
    }
}