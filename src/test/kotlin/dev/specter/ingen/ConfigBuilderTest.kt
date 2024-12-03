package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.util.TestConstants
import org.junit.After
import org.junit.Before
import org.junit.Test
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
                assert(it.command.description == EXPECTED_TEST_STRING)
            }
        } ?: fail()
    }

    @Test
    fun testGenerateDefaultFiles() {
        ConfigBuilder.initializeFS()
        assert(ConfigBuilder.generateDefaultFiles())
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
        val config = ConfigBuilder.buildCommands()
        assert(
            config
                ?.any { it.command.description == EXPECTED_TEST_TAG } == true
        )
    }

    companion object {
        private const val EXPECTED_TEST_STRING = "TEST"
        private const val EXPECTED_TEST_PATH = "/bin/echo"
        private const val EXPECTED_TEST_TAG = "echo using /usr/bin/echo"
    }
}
