@file:OptIn(DelicateCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.TestConstants
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread
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
        GlobalScope.launch {
            // we delay to avoid thread-based race conditions, allowing all
            // subprocess cleanup to take place before they are killed
            delay(5000)
            commander.killAll()
        }
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
    fun test_poll_exec_explicit() {
        val out = commander.executeExplicit(ECHO_PATH, listOf(ECHO_CONTENT)).trimEnd()
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

    /**
     * This test represents a "cold" subprocess - aka, one that is NOT wrapped within some kind of async
     * framework at its own language level (i.e., asyncIO for Python); irrespective of the thread management/scheduling
     * at the Kotlin level, these types of subprocesses will NOT return a hot flow that is accessible on-demand; that
     * said, the behavior processor will NOT act as a traditional cold flow, wherein the subprocess gets re-executed
     * on each subscription; in such a case, the subprocess will need to exit before the results can be accessed by the
     * subscriber as a single "batch" result
     */
    @Test
    fun test_execute_explicit_rx_cold() {
        val out = arrayListOf<String>()
        val op = BehaviorProcessor.create<String>()

        op.toObservable()
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeBy(
                onNext = {
                    println("Test output received: $it")
                    out.add(it)
                },
                onError = { fail("Explicit RX test error...") }
            )

        commander.executeExplicitRx(
            commandPath = PYTHON_PATH,
            args = listOf(EXPLICIT_RX_SCRIPT_PATH),
            workingDir = IngenConfig.INGEN_DEFAULT_DIR,
            pid = 101010,
            outputPublisher = op
        )

        runTest { assert(out.size == EXPECTED_EXPLICIT_RX_RESULTS_SIZE) }
    }

    /**
     * Redundant test of above scenario, wherein asyncIO is used within Python to mitigate the "cold" flow behavior when
     * wrapping Python, but it appears to have no impact; curiously, the shell scripts do not display this same behavior
     */
    @Test
    fun test_execute_explicit_rx_asyncio() {
        val out = arrayListOf<String>()
        val op = BehaviorProcessor.create<String>()

        op.toObservable()
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeBy(
                onNext = {
                    println("Test output received: $it")
                    out.add(it)
                },
                onError = { fail("Explicit RX test error...") }
            )

        commander.executeExplicitRx(
            commandPath = PYTHON_PATH,
            args = listOf(EXPLICIT_RX_SCRIPT_PATH2),
            workingDir = IngenConfig.INGEN_DEFAULT_DIR,
            pid = 101010,
            outputPublisher = op
        )

        runTest { assert(out.size == EXPECTED_EXPLICIT_RX_RESULTS_SIZE) }
    }

    // Test interactive subprocess management, specific to a Python subprocess
    @Test
    fun test_subprocess_python_input_channel() {
        val out = arrayListOf<String>()
        val ip = BehaviorProcessor.create<String>()
        val op = BehaviorProcessor.create<String>()

        op.toObservable()
            .toFlowable(BackpressureStrategy.LATEST)
            .observeOn(Schedulers.io())
            .filter { it.isNotEmpty() }
            .subscribeBy(
                onNext = {
                    println("Flowable observed: $it")
                    out.add(it)
                },
                onError = {
                    fail(("Flowable test error: ${it.localizedMessage}"))
                }
            )

        // timed interactive console inputs
        val job = GlobalScope.async {
            delay(3000)
            ip.onNext("test1")
            delay(1000)
            ip.onNext("test2")
            delay(1000)
            ip.onNext("xx")
        }

        // simulated service thread wrapper
        thread {
            commander.executeExplicitInteractive(
                programPath = PYTHON_PATH,
                args = listOf(INTERACTIVE_MODULE_PATH),
                workingDir = IngenConfig.INGEN_DEFAULT_DIR,
                pid = 10001,
                inputPublisher = ip,
                outputPublisher = op
            )
//            commander.monitorControlChannel(
//                args = INPUT_TEST_ARGS,
//                directory = TEST_DIR,
//                inputPublisher = ip,
//                outputPublisher = op
//            )
        }

        // start job after subprocess is launched
        job.start()

        // assert after awaiting input simulation job
        runTest {
            job.await()
            delay(2000)
            assert(out.size == EXPECTED_INPUT_RESULT_SIZE_PYTHON)
        }
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
    fun test_execute_async() {
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
        private val USER_HOME = System.getProperty("user.home")
        private const val ECHO_CONTENT = "test echo"
        private const val EXPECTED_MONITOR_OUTPUT_SIZE = 10
        private const val EXPECTED_COROUTINES_RESULTS_SIZE = 5
        private const val ECHO_PATH = "/bin/echo"
        private val PYTHON_PATH = "$USER_HOME/.pyenv/shims/python"
        private val INTERACTIVE_MODULE_PATH = "${IngenConfig
            .INGEN_MODULE_DIR}/input_tester.py"
        private const val EXPECTED_INPUT_RESULT_SIZE_PYTHON = 2
        private val EXPLICIT_RX_SCRIPT_PATH = "${TestConstants.TEST_RES_DIR}/test_emitter.py"
        private val EXPLICIT_RX_SCRIPT_PATH2 = "${TestConstants.TEST_RES_DIR}/test_async_emitter.py"

        private const val EXPECTED_EXPLICIT_RX_RESULTS_SIZE = 5

    }
}
