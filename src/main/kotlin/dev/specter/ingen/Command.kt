package dev.specter.ingen

import dev.specter.ingen.ProcessType.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contract for all commands harvested and decoded from JSON spec files
 *
 * @property programCode ordinal path code of referenced program
 * @property typeCode ordinal path code of referenced program
 * @property directory working directory for command
 * @property programAlias name of helper/script files, i.e., `runner.py`
 * @property escapeSequence key sequence for signaling process termination
 * @property description brief explanation of referenced command
 *
 */
interface ICommand {
    val programCode: Int
    val typeCode: Int
    val programAlias: String
    val directory: String
    val escapeSequence: String?
    val description: String
}

/**
 * Executable subprocess object schema, containing both the specific subprocess
 * command and the necessary logistical information for process management
 * purposes
 *
 * @property callerKey unique command ID for runtime logistics
 * @property command nested command object
 */
interface ISubprocess {
    val callerKey: String
    val command: ICommand

    /**
     * Argument list builder method for compiling a command with user-supplied
     * arguments where applicable, such as from UI elements like text/numbers
     *
     * @param userArgs list of user-supplied command arguments
     * @return compiled list of all command args, including user-supplied
     */
    fun toArgsList(userArgs: List<String>): List<String> {
        return with(arrayListOf<String>()) {
            add(command.programAlias)
            addAll(userArgs)
            filter { it.isNotBlank() }
        }
    }
}

/**
 * Simple process type designation for executing subprocesses
 *
 * @property POLL simple "fire-and-forget" command that returns a single result
 * @property ASYNC async subprocess that accepts no user input
 * @property INTERACTIVE async subprocess that accepts user input
 */
enum class ProcessType {
    POLL,
    ASYNC,
    INTERACTIVE
}

/**
 * Native commands and their paths to system resources from elsewhere within
 * the embedded firmware; treat this class as the master "schema" for defining
 * the execution of subprocesses from within the JRE on the embedded system
 * during runtime
 */
@Serializable
data class Command(
    @SerialName("pcode")
    override val programCode: Int,
    @SerialName("tcode")
    override val typeCode: Int,
    @SerialName("alias")
    override val programAlias: String,
    @SerialName("dir")
    override val directory: String,
    @SerialName("esc")
    override val escapeSequence: String? = null,
    @SerialName("desc")
    override val description: String
): ICommand {
    val processType: ProcessType
        get() = ProcessType.entries.first { it.ordinal == typeCode }
}

/**
 * Contains all information needed to spawn and monitor a subprocess from within
 * an Ingen instance
 */
@Serializable
data class Subprocess(
    override val callerKey: String,
    @Serializable
    override val command: Command
) : ISubprocess

