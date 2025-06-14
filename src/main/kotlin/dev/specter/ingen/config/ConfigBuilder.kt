package dev.specter.ingen.config

import dev.specter.auxi.FSHelper
import dev.specter.ingen.Subprocess
import dev.specter.ingen.config.IngenDefaults.CMD_PATH
import dev.specter.ingen.config.IngenDefaults.CONFIG_PATH
import dev.specter.ingen.config.IngenDefaults.MODULE_1_PATH
import dev.specter.ingen.config.IngenDefaults.MODULE_2_PATH
import dev.specter.ingen.config.IngenDefaults.MODULE_3_PATH
import dev.specter.ingen.util.CommandConstants
import dev.specter.ingen.util.Logger
import dev.specter.ingen.util.SerializationHandler
import java.nio.file.Files
import kotlin.io.path.exists

/**
 * Utility object for quickly parsing subprocess commands from a given JSON file
 */
object ConfigBuilder {

    fun initializeFS() {
        with(FSHelper) {
            val runtimeDirCreated = createPathDirectories(IngenConfig.INGEN_DEFAULT_DIR)
            val configDirCreated = createPathDirectories(IngenConfig.INGEN_CONFIG_DIR)
            val logDirCreated = createPathDirectories(IngenConfig.INGEN_LOG_DIR)
            val moduleDirCreated = createPathDirectories(IngenConfig.INGEN_MODULE_DIR)
            Logger.debug("Newly created paths: \n" +
                    "\tRuntime dir: $runtimeDirCreated\n" +
                    "\tConfig dir: $configDirCreated\n" +
                    "\tLog dir: $logDirCreated\n" +
                    "\tModule dir: $moduleDirCreated\n")
        }
        generateDefaultFiles()
    }

    // TODO: abstract this type of file creation and overwriting in Auxi
    fun generateDefaultFiles(): Boolean = try {
        if (CMD_PATH.exists().not()) {
            Files.write(
                CMD_PATH,
                IngenDefaults.DEFAULT_COMMANDS.toByteArray(),
            )
        }
        if (CONFIG_PATH.exists().not()) {
            Files.write(
                CONFIG_PATH,
                IngenDefaults.DEFAULT_CONFIG.toByteArray(),
            )
        }
        if (MODULE_1_PATH.exists().not()) {
            Files.write(
                MODULE_1_PATH,
                ScriptDefaults.INTERACTIVE_PY_TEST_SCRIPT.toByteArray(),
            )
        }
        if (MODULE_2_PATH.exists().not()) {
            Files.write(
                MODULE_2_PATH,
                ScriptDefaults.ASYNC_EMITTER_SH_TEST_SCRIPT.toByteArray(),
            )
        }
        if (MODULE_3_PATH.exists().not()) {
            Files.write(
                MODULE_3_PATH,
                ScriptDefaults.ASYNC_EMITTER_PY_TEST_SCRIPT.toByteArray(),
            )
        }
        true
    } catch (e: Exception) {
        Logger.error(e)
        false
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
