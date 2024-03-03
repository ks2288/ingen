import command.Command
import command.ProcessType
import command.Subprocess
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import message.SerializationHandler
import net.il.Commander
import net.il.util.SysConstants
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class CommandExecutionTest {
    private lateinit var commander: Commander
    private lateinit var jsonArrayString: String
    private lateinit var outputPublisher: BehaviorProcessor<String>
    private lateinit var inputPublisher: BehaviorProcessor<String>
    private var testArray: JsonArray? = null
    private val execList = arrayListOf<Subprocess>()
    private lateinit var mockAsyncCommandRx: Command
    private lateinit var mockAsyncCommandCoroutines: Command

    @Before
    fun setup() {
        commander = Commander()
        outputPublisher = BehaviorProcessor.create()
        inputPublisher = BehaviorProcessor.create()
        jsonArrayString = File(JSON_ARRAY_FILE_PATH).readText()
        testArray = SerializationHandler.parseJsonArrayFromString(jsonArrayString)
        testArray?.forEach {
            val decoded = SerializationHandler.serializableFromElement<Subprocess>(it)

            decoded?.let { sp -> execList.add(sp) }
        }
        mockAsyncCommandRx = Command(
            programAlias = MOCK_SCANNER_ALIAS,
            directory = TEST_RES_DIR,
            typeCode = ProcessType.ASYNC.ordinal,
            pathCode = 1,
            tag = "test executable value emitter shell script"
        )

        mockAsyncCommandCoroutines = Command(
            programAlias = MOCK_EMITTER_ALIAS,
            directory = "",
            typeCode = ProcessType.ASYNC.ordinal,
            pathCode = 3,
            tag = "test executable value emitter python script"
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
            pathCode = 2,
            tag = "test command using /bin/echo"

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
    fun test_subproc_monitor_rx() {
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
                    throw it
                },
                onComplete = {
                    assert(out.size == EXPECTED_MONITOR_OUTPUT_SIZE)
                }
            )

        commander.monitorSubprocess(
            executable = exec,
            userArgs = listOf(),
            outputPublisher = outputPublisher
        )
    }

    @Test
    fun test_process_monitor() {
        runTest {
            val out = arrayListOf<String>()

            val sp = Subprocess(
                id = 2,
                command = mockAsyncCommandCoroutines
            )

            commander.subprocessChannelFlow(
                executable = sp,
                userArgs = listOf(),
            ).collect {
                println("Callback flow collected: $it")
                out.add(it)
            }

            assert(out.size == EXPECTED_COROUTINES_RESULTS_SIZE)
        }
    }

    companion object {
        private val TEST_RES_DIR = "${SysConstants.PROJECT_ROOT}/src/test/resources"
        private const val JSON_ARRAY_FILE_NAME = "test_cmd.json"
        private val JSON_ARRAY_FILE_PATH = "$TEST_RES_DIR/$JSON_ARRAY_FILE_NAME"
        private const val ECHO_CONTENT = "test echo"
        private const val MOCK_SCANNER_ALIAS = "./mock_async_emitter.sh"
        private val MOCK_EMITTER_ALIAS = "${TEST_RES_DIR}/test_emitter.py"
        private const val EXPECTED_MONITOR_OUTPUT_SIZE = 10
        private const val EXPECTED_COROUTINES_RESULTS_SIZE = 5
    }
}