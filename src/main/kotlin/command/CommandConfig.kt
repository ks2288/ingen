package command

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Simple interface for native program path definition conformance
 *
 * @property code arbitrary, unique program code for use during runtime
 * @property path system-specific path for the designated program
 */
interface IProgram {
    val code: Int
    val path: String
}

/**
 * Concrete class for all program definitions produced through JSON config file serialization
 */
@Serializable
data class Program(
    @SerialName("CODE")
    override val code: Int,
    @SerialName("PATH")
    override val path: String
) : IProgram

/**
 * Concrete class for master command configuration instances, produced through JSON config file serialization
 */
@Serializable
data class CommandConfig(
    @SerialName("PATH_MAP")
    val paths: Map<String, Program> = mapOf(),
    @SerialName("RUNTIME_DIR")
    val runtimeDirectory: String = System.getProperty("user.home"),
    @SerialName("ENV")
    val environmentVariables: Map<String, String> = mapOf()
) {
    /**
     * Simple produces a list of command names for easier designation throughout the rest of the JRE app
     */
    fun getCommandNames() = with(arrayListOf<String>()) {
        paths.entries.forEach { add(it.key) }
        this.toTypedArray()
    }
}
