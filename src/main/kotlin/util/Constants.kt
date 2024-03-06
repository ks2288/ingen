package net.il.util

import net.il.IngenConfig
// TODO: look into why IDEA is auto-generating these on auto-optimization;
//  they're from this file
import net.il.util.CommandConstants.COMMAND_FILE_NAME
import net.il.util.CommandConstants.COMMAND_FILE_PATH
import net.il.util.CommandConstants.CONFIG_FILE_NAME
import net.il.util.CommandConstants.CONFIG_FILE_PATH
import net.il.util.CommandConstants.LOG_DIR

/**
 * Utility object for organizing known locations on the host system from which
 *
 * @property CONFIG_FILE_NAME relative name/path of configuration spec file
 * @property COMMAND_FILE_NAME relative name/path of command spec file
 * @property CONFIG_FILE_PATH absolute path of configuration spec file
 * @property COMMAND_FILE_PATH absolute path of command spec file
 * @property LOG_DIR absolute path to the directory of generated log files
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
}
