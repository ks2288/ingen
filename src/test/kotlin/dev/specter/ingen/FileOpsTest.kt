@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.CommandExecutionTest.Companion.PYTHON_PATH
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.CommandConstants
import dev.specter.ingen.util.Logger
import dev.specter.ingen.util.TestConstants
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        GlobalScope.launch {
            delay(5000)
            commander.killAll()
        }
    }

    @Test
    fun test_file_watcher_async() {
        val out = arrayListOf<String>()
        val wd = "${TestConstants.TEST_MODULE_DIR}/fw"
        fwPublisher.toObservable().toFlowable(BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.io())
            .subscribeBy(
                onNext = {
                    Logger.debug("File watcher output received:\n\n$it")
                    out.add(it)
                },
                onError = { fail("File watcher test error: ${it.localizedMessage}") },
                onComplete = { return@subscribeBy }
            )
        val channel: Channel<Int> = Channel()

        // spawn a thread for the file watcher; necessary for real-time (hot) flow of data
        thread {
            commander.spawnFileWatch(
                watchDirectory = wd,
                outputPublisher = fwPublisher,
                killChannel = channel,
            )
        }

        // block main thread and run test scenario with separate subprocess
        runTest {
            commander.executeExplicitRx(
                commandPath = PYTHON_PATH,
                args = listOf(TEST_FILE_WRITER_PATH, "${TestConstants.TEST_MODULE_DIR}/fw"),
                workingDir = IngenConfig.INGEN_DEFAULT_DIR,
                callerKey = "101010",
                outputPublisher = fwPublisher
            )
            // allow a bit of time for thread ops to maintain/get to good state
            delay(5000)
            // assert after delay (currently just checking for callback ability)
            assert(out.isNotEmpty())
            // send the SIG_KILL to the kill channel to break the threaded method's loop
            channel.send(CommandConstants.SIG_KILL.toInt())
        }
    }

    companion object {
        val TEST_FILE_WRITER_PATH = "${TestConstants.TEST_RES_DIR}/test_file_writer.py"
    }
}