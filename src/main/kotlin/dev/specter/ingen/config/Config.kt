package dev.specter.ingen.config

import dev.specter.ingen.ProcessType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Simple interface for native program path definition conformance
 *
 * @property code arbitrary, unique program code for use during runtime
 * @property path system-specific path for the designated program
 * @property processType computed property for retrieving a process type via [code] value
 */
interface IProgram {
    val code: Int
    val path: String
    val processType: ProcessType
        get() = ProcessType.entries.first { it.ordinal == code }
}

/**
 * Concrete class for all program definitions produced through JSON config file
 * serialization/decoding
 */
@Serializable
data class Program(
    @SerialName("CODE")
    override val code: Int,
    @SerialName("PATH")
    override val path: String
) : IProgram

/**
 * Concrete class for master command configuration instances, produced through
 * JSON config file serialization
 */
@Serializable
data class IngenConfig(
    @SerialName("PATH_MAP")
    val paths: Map<String, Program> = mapOf(),
    @SerialName("RUNTIME_DIR")
    val runtimeDirectory: String = INGEN_DEFAULT_DIR,
    @SerialName("ENV")
    val envVar: Map<String, String> = mapOf()
) {
    /**
     * Produces a list of command names for easier designation throughout the
     * rest of the JRE app
     */
    fun getCommandNames() = with(arrayListOf<String>()) {
        paths.entries.forEach { add(it.key) }
        this.toTypedArray()
    }

    companion object {
        /**
         * Default fallback runtime directory definition for situations
         * wherein normal file system access is not available for using this
         * library traditionally; generally speaking, that will render this
         * library difficult to leverage outside of test scenarios
         */
        val INGEN_DEFAULT_DIR = System.getProperty("user.home") + "/.ingen"
        val INGEN_CONFIG_DIR = "$INGEN_DEFAULT_DIR/config"
        val INGEN_LOG_DIR = "$INGEN_DEFAULT_DIR/log"
        val INGEN_MODULE_DIR = "$INGEN_DEFAULT_DIR/module"
    }
}
