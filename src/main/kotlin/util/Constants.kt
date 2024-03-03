package net.il.util

import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object CommandConstants {
    private const val SCHEMA_FILE_NAME = "command.json"
    private const val PROGRAM_PATHS_FILE_NAME = "config/ingen.json"
    val COMMAND_SCHEMA_PATH =
        "${SysConstants.PROJECT_ROOT}/$SCHEMA_FILE_NAME"
    const val PROGRAM_PATHS_PATH = "json/$PROGRAM_PATHS_FILE_NAME"
}

object SysConstants {
    val PROJECT_ROOT = Path("").absolutePathString()
}