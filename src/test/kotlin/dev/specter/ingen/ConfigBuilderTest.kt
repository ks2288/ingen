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
    fun test_build_commands() {

        val commands = ConfigBuilder.buildSubprocesses(TestConstants.TEST_COMMANDS_JSON_PATH)
        commands?.let { c ->
            c.forEach {
                assert(it.command.description == EXPECTED_TEST_STRING)
            }
        } ?: fail()
    }

    @Test
    fun test_generate_default_files() {
        ConfigBuilder.initializeFS()
        assert(ConfigBuilder.generateDefaultFiles())
    }

    @Test
    fun test_build_config_from_file() {
        val config = ConfigBuilder.buildConfig(TestConstants.TEST_CONFIG_FILE_PATH)
        with(config) {
            assert(runtimeDirectory == EXPECTED_TEST_STRING)
        }
    }

    @Test
    fun test_parse_config_from_defaults() {
        // intentionally give this a bad path to force default val parsing
        val config = ConfigBuilder.buildConfig("")
        assert(
            config
                .paths
                .any { it.value.path == EXPECTED_TEST_PATH }
        )
    }

    companion object {
        private const val EXPECTED_TEST_STRING = "TEST"
        private const val EXPECTED_TEST_PATH = "/bin/echo"
    }
}
