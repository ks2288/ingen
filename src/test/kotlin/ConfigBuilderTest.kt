import command.ConfigBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test

class ConfigBuilderTest {
    @Before
    fun setup() {}

    @After
    fun teardown() {}

    @Test
    fun testBuildCommandsFromFile() {
        val commands = ConfigBuilder.buildCommands()
        assert(commands?.size == EXPECTED_COMMAND_SIZE)
    }

    @Test
    fun testBuildConfigFromFile() {
        val config = ConfigBuilder.buildConfig()
        assert(config != null)
    }

    companion object {
        private const val EXPECTED_COMMAND_SIZE = 8
    }
}