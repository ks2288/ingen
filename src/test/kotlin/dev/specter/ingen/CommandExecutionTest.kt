package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.util.TestConstants
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

class CommandExecutionTest {
    private lateinit var commander: Commander
    private lateinit var outputPublisher: BehaviorProcessor<String>
    private lateinit var inputPublisher: BehaviorProcessor<String>
    private lateinit var mockAsyncCommandRx: Command
    private lateinit var mockAsyncCommandCoroutines: Command

    @Before
    fun setup() {
        ConfigBuilder.initializeFS()
        commander = Commander()
        outputPublisher = BehaviorProcessor.create()
        inputPublisher = BehaviorProcessor.create()

        mockAsyncCommandRx = Command(
            programAlias = TestConstants.ASYNC_SHELL_SCRIPT_PATH,
            directory = "",
            typeCode = ProcessType.ASYNC.ordinal,
            programCode = 0,
            description = "test executable value emitter shell script"
        )

        mockAsyncCommandCoroutines = Command(
            programAlias = TestConstants.ASYNC_PYTHON_SCRIPT_PATH,
            directory = "",
            typeCode = ProcessType.ASYNC.ordinal,
            programCode = 3,
            description = "test executable value emitter python script"
        )
    }

    @After
    fun teardown() {
        commander.killAll()
    }

    @Test
    fun test_poll_exec() {
        val userArgs = listOf(ECHO_CONTENT)
        val nc = Command(
            programAlias = "",
            directory = "",
            typeCode = ProcessType.POLL.ordinal,
            programCode = 2,
            description = "test command using /bin/echo"
        )
        val echoCmd = Subprocess(
            id = 1,
            command = nc
        )

        // trim end to remove newline
        val out = commander.execute(echoCmd, userArgs).trimEnd()
        assert(out == ECHO_CONTENT)
    }

    @Test
    fun test_execute_rx() {
        val exec = Subprocess(
            command = mockAsyncCommandRx,
            id = 0
        )
        val out = arrayListOf<String>()

        outputPublisher
            .toObservable()
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeBy(
                onNext = {
                    println("*** I/O: $it ***")
                    out.add(it)
                },
                onError = {
                    fail()
                },
                onComplete = {}
            )

        commander.executeRx(
            executable = exec,
            userArgs = listOf(),
            outputPublisher = outputPublisher
        )

        runTest { assert(out.size == EXPECTED_MONITOR_OUTPUT_SIZE) }
    }

    @Test
    fun test_execute_channel_flow() {
        runTest {
            val out = arrayListOf<String>()
            val sp = Subprocess(
                id = 2,
                command = mockAsyncCommandCoroutines
            )

            commander.executeChannelFlow(
                executable = sp,
                userArgs = listOf(),
            ).collect {
                println("Callback flow collected: $it")
                out.add(it)
            }

            assert(out.size == EXPECTED_COROUTINES_RESULTS_SIZE)
        }
    }

    @Test
    fun testExecuteAsync() {
        val channel = Channel<String>()
        val out = arrayListOf<String>()
        val sp = Subprocess(
            id = 3,
            command = mockAsyncCommandCoroutines
        )
        runTest {
            launch {
                commander.collectAsync(
                    executable = sp,
                    userArgs = listOf(),
                    channel = channel
                )
            }

            val job = async {
                channel.consumeAsFlow().collect {
                    println("consumer collected output: $it")
                    out.add(it)
                }
            }
            job.start()
            job.await()

            assert(out.size == EXPECTED_COROUTINES_RESULTS_SIZE)
        }
    }

    companion object {
        private const val JSON_ARRAY_FILE_NAME = "test_cmd.json"
        private val JSON_ARRAY_FILE_PATH =
            "${TestConstants.TEST_RES_DIR}/$JSON_ARRAY_FILE_NAME"
        private const val ECHO_CONTENT = "test echo"
        private const val EXPECTED_MONITOR_OUTPUT_SIZE = 10
        private const val EXPECTED_COROUTINES_RESULTS_SIZE = 5
    }
}
