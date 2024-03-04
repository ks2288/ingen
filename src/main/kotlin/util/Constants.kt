package net.il.util

import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object CommandConstants {
    private const val COMMANDS_FILE_NAME = "config/commands.json"
    private const val CONFIG_FILE_NAME = "config/ingen.json"
    private val DEPLOYMENT_DIRECTORY = "${System.getProperty("user.home")}/.ingen"
    val COMMAND_SCHEMA_PATH =
        "$DEPLOYMENT_DIRECTORY/$COMMANDS_FILE_NAME"
    val CONFIG_FILE_PATH = "$DEPLOYMENT_DIRECTORY/$CONFIG_FILE_NAME"
    val LOG_DIR = "$DEPLOYMENT_DIRECTORY/log"
}

object SysConstants {
    val PROJECT_ROOT = Path("").absolutePathString()
}