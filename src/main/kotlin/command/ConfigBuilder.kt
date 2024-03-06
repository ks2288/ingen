package command

import message.SerializationHandler
import net.il.IngenConfig
import net.il.IngenDefaults
import net.il.util.CommandConstants
import net.il.util.FSHelper
import net.il.util.Logger
import java.io.File

/**
 * Utility object for quickly parsing subprocess commands from a given JSON file
 */
object ConfigBuilder {

    fun initializeFS() {
        val pathSuccess =
            FSHelper.createPathDirectories(IngenConfig.INGEN_DEFAULT_DIR)
        Logger.debug("System paths initialized: $pathSuccess")
    }

    fun getIngenRuntimeDirectory(): File? = try {
        File(IngenConfig.INGEN_DEFAULT_DIR)
    } catch (e: Exception) {
        Logger.error(e)
        null
    }
    /**
     * Takes a path of a subprocess command schema, and parses the file contents
     * into a list of subprocess command objects
     *
     * @param schemaPath path to file from which the JSON text will be read
     * @return list of decoded subprocess objects
     */
    fun buildCommands(
        schemaPath: String = CommandConstants.COMMAND_FILE_PATH
    ): List<Subprocess>? = try {
        FSHelper.getFileText(schemaPath)?.let {
            val list = parseCommands(it)
            with(arrayListOf<Subprocess>()) {
                list?.forEach { sp -> add(sp) }
                this
            }
        } ?: parseCommands(IngenDefaults.DEFAULT_COMMANDS)
    } catch (e: Exception) {
        Logger.error(e)
        null
    }

    /**
     * Takes a path of a program path configuration JSON file, and reads/parses
     * the text into an [IngenConfig] instance
     *
     * @param configPath path to file located within module resource directory
     * @return decoded command config object
     */
    fun buildConfig(
        configPath: String = CommandConstants.CONFIG_FILE_PATH
    ): IngenConfig? = try {
        FSHelper.getFileText(configPath)?.let { s ->
            SerializationHandler.serializableFromString(s)
        } ?: SerializationHandler
            .serializableFromString(IngenDefaults.DEFAULT_CONFIG)

    } catch (e: Exception) {
        Logger.error(e)
        null
    }

    /**
     * Compartmentalized method for handling the parsing of the schema file text
     * into operable subprocess objects
     *
     * @param schemaString file content as a string
     * @return list of parsed subprocess objects
     */
    private fun parseCommands(schemaString: String): List<Subprocess>? = try {
        val cmdArray = SerializationHandler
            .parseJsonArrayFromString(schemaString)
        with(arrayListOf<Subprocess>()) {
            cmdArray?.forEach {
                SerializationHandler
                    .serializableFromElement<Subprocess>(it)?.let { cmd ->
                        add(cmd)
                    }
            }
            toList()
        }
    } catch (e: Exception) {
        Logger.error(e)
        null
    }
}
