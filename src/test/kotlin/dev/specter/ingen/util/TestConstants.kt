@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.specter.ingen.util

import dev.specter.ingen.config.IngenConfig
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object TestConstants {
    private val PROJECT_ROOT = Path("").absolutePathString()
    private val SAMPLE_CODE_PATH = "$PROJECT_ROOT/setup/samples"
    val TEST_RES_DIR = "$PROJECT_ROOT/src/test/resources"
    val TEST_COMMANDS_JSON_PATH = "$TEST_RES_DIR/test_cmd.json"
    val TEST_CONFIG_FILE_PATH = "$TEST_RES_DIR/test_config.json"
    val ASYNC_SHELL_SCRIPT_PATH = "$SAMPLE_CODE_PATH/async_echo.sh"
    val ASYNC_PYTHON_SCRIPT_PATH = "$SAMPLE_CODE_PATH/async_echo.py"
    val TEST_MODULE_DIR = "${IngenConfig.INGEN_DEFAULT_DIR}/test"
}
