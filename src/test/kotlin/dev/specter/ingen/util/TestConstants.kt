@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.specter.ingen.util

import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object TestConstants {
    private val PROJECT_ROOT = Path("").absolutePathString()
    private val SAMPLE_CODE_PATH = "$PROJECT_ROOT/setup/samples"
    val TEST_UBYTE = 0x01.toUByte()
    val TEST_UBYTE_CRC16 = 4129.toUShort()
    val TEST_PACKET_DATA = ubyteArrayOf(
        0x10u, 0x11u, 0x12u, 0x13u, 0x14u, 0x15u, 0x16u, 0x17u,
        0x20u, 0x21u, 0x22u, 0x23u, 0x24u, 0x25u, 0x26u, 0x27u,
        0x10u, 0x11u, 0x12u, 0x13u, 0x14u, 0x15u, 0x16u, 0x17u,
        0x20u, 0x21u, 0x22u, 0x23u, 0x24u, 0x25u, 0x26u, 0x27u,
        0x10u, 0x11u, 0x12u, 0x13u, 0x14u, 0x15u, 0x16u, 0x17u,
        0x20u, 0x21u, 0x22u, 0x23u, 0x24u, 0x25u, 0x26u, 0x27u,
        0x10u, 0x11u, 0x12u, 0x13u, 0x14u, 0x15u, 0x16u, 0x17u,
        0x20u, 0x21u, 0x22u, 0x23u, 0x24u, 0x25u, 0x26u, 0x27u,
    )
    val TEST_RES_DIR = "$PROJECT_ROOT/src/test/resources"
    val TEST_UBYTE_ARRAY_CRC16 = 17850.toUShort()
    val TEST_COMMAND_FILE_PATH = "$TEST_RES_DIR/test_cmd.json"
    val TEST_CONFIG_FILE_PATH = "$TEST_RES_DIR/test_config.json"
    val ASYNC_SHELL_SCRIPT_PATH = "$SAMPLE_CODE_PATH/async_echo.sh"
    val ASYNC_PYTHON_SCRIPT_PATH = "$SAMPLE_CODE_PATH/async_echo.py"
}
