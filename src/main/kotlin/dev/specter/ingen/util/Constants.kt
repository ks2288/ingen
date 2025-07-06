package dev.specter.ingen.util

import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.CommandConstants.COMMAND_FILE_NAME
import dev.specter.ingen.util.CommandConstants.COMMAND_FILE_PATH
import dev.specter.ingen.util.CommandConstants.CONFIG_FILE_NAME
import dev.specter.ingen.util.CommandConstants.CONFIG_FILE_PATH
import dev.specter.ingen.util.CommandConstants.LOG_DIR
import dev.specter.ingen.util.CommandConstants.SIG_KILL

/**
 * Utility object for organizing known locations on the host system from which
 *
 * @property CONFIG_FILE_NAME relative name/path of configuration spec file
 * @property COMMAND_FILE_NAME relative name/path of command spec file
 * @property CONFIG_FILE_PATH absolute path of configuration spec file
 * @property COMMAND_FILE_PATH absolute path of command spec file
 * @property LOG_DIR absolute path to the directory of generated log files
 * @property SIG_KILL arbitrary ubyte to be sent to control channels for SP destruction
 */
object CommandConstants {
    private const val CONFIG_FILE_NAME = "/config/ingen.json"
    private const val COMMAND_FILE_NAME = "/config/commands.json"
    val CONFIG_FILE_PATH =
        "${IngenConfig.INGEN_DEFAULT_DIR}/$CONFIG_FILE_NAME"
    val COMMAND_FILE_PATH =
        "${IngenConfig.INGEN_DEFAULT_DIR}/$COMMAND_FILE_NAME"
    val LOG_DIR =
        "${IngenConfig.INGEN_DEFAULT_DIR}/log"
    val SIG_KILL: UByte = 0xFFu
}
