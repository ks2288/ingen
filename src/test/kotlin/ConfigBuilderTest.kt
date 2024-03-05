import command.ConfigBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import util.TestConstants
import kotlin.test.fail

class ConfigBuilderTest {
    @Before
    fun setup() {}

    @After
    fun teardown() {}

    @Test
    fun testBuildCommandsFromFile() {
        val commands = ConfigBuilder.buildCommands(TestConstants.TEST_COMMAND_FILE_PATH)
        commands?.let { c ->
            c.forEach {
                assert(it.command.tag == EXPECTED_TEST_STRING)
            }
        } ?: fail()
    }

    @Test
    fun testBuildConfigFromFile() {
        val config = ConfigBuilder.buildConfig(TestConstants.TEST_CONFIG_FILE_PATH)
        with(config) {
            this?.let {
                assert(it.runtimeDirectory == EXPECTED_TEST_STRING)
            } ?: fail()
        }
    }

    @Test
    fun testParseConfigFromDefaults() {
        // intentionally give this a bad path to force default val parsing
        val config = ConfigBuilder.buildConfig("")
        assert(
            config
                ?.paths
                ?.any { it.value.path == EXPECTED_TEST_PATH } == true
        )
    }

    @Test
    fun testParseCommandsFromDefaults() {
        // intentionally give this a bad path to force default val parsing
        val config = ConfigBuilder.buildCommands("")
        assert(
            config
                ?.any { it.command.tag == EXPECTED_TEST_TAG } == true
        )
    }

    companion object {
        private const val EXPECTED_TEST_STRING = "TEST"
        private const val EXPECTED_TEST_PATH = "/bin/echo"
        private const val EXPECTED_TEST_TAG = "simple echo command"
    }
}