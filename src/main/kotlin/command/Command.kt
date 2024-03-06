package command

import command.ProcessType.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contract for all commands harvested and decoded from JSON spec files
 *
 * @property tag description of referenced program
 * @property directory working directory for command
 * @property programAlias name of helper/script files, i.e., `runner.py`
 * @property escapeSequence key sequence for signaling process termination
 * @property pathCode ordinal path code of referenced program
 * @property typeCode ordinal type code of referenced program
 */
interface ICommand {
    val tag: String
    val directory: String
    val programAlias: String
    val escapeSequence: String?
    val pathCode: Int
    val typeCode: Int
}

/**
 * Simple contract for cataloging subprocesses during runtime
 *
 * @property process native process object
 * @property sequenceNumber value for auditing the sequence of process events
 */
interface ISession {
    val process: Process
    val subprocessId: Int
    var sequenceNumber: Int
}

/**
 * Executable subprocess object schema, containing both the specific subprocess
 * command and the necessary logistical information for process management
 * purposes
 *
 * @property id unique subprocess ID for runtime logistics
 * @property command nested command object
 */
interface ISubprocess {
    val id: Int
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
// TODO: create an annotation for all reflector classes etc that have a backing JSON config
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
    override val tag: String,
    @SerialName("alias")
    override val programAlias: String,
    override val directory: String,
    @SerialName("escape")
    override val escapeSequence: String? = null,
    override val pathCode: Int,
    override val typeCode: Int
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
    override val id: Int,
    @Serializable
    override val command: Command
) : ISubprocess

/**
 * Concrete implementation for storing information about active subprocesses
 */
class Session(
    override val process: Process,
    override val subprocessId: Int
) : ISession {
    override var sequenceNumber: Int = 0
}
