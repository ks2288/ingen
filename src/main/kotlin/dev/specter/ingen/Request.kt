package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.processors.BehaviorProcessor

/// Route "package" for all async ops, including mandatory output publisher and optional input publisher
typealias IORoute = Pair<BehaviorProcessor<String>, BehaviorProcessor<String>?>

interface ILaunchRequest {
    val callerKey: String
    val programPath: String
    val workingDir: String
    val userArgs: List<String>
    val envVars: Map<String, String>

    companion object {
        fun create(
            key: String,
            path: String,
            directory: String = IngenConfig.INGEN_DEFAULT_DIR,
            args: List<String> = listOf(),
            env: Map<String, String> = mapOf()
        ) : ILaunchRequest {
            return object : ILaunchRequest {
                override val callerKey: String = key
                override val programPath: String = path
                override val workingDir: String = directory
                override val userArgs: List<String> = args
                override val envVars: Map<String, String> = env
            }
        }

        fun create(
            key: String,
            uid: Int,
            args: List<String> = listOf(),
            env: Map<String, String> = mapOf()
        ): ILaunchRequest? = try {
            ConfigBuilder.buildSubprocesses()
                ?.first{ it.uid == uid.toString() }
                ?.let { sp ->
                    val prog = Dispatcher.config.paths.entries.first { p ->
                        p.value.code == sp.command.programCode
                    }.value
                    create(
                        key = key,
                        path = prog.path,
                        directory = sp.command.directory.takeIf { it.isNotEmpty() } ?: IngenConfig.INGEN_DEFAULT_DIR,
                        args = sp.toArgsList(userArgs = args),
                        env = env
                    )
                }
        } catch (e: Exception) {
            Logger.error(e)
            null
        }
    }
}

interface IFileWatchRequest {
    val callerKey: String
    val watchDirectory: String
    val isRecursive: Boolean

    companion object {
        fun create(
            key: String,
            directory: String,
            recursive: Boolean = true
        ) : IFileWatchRequest {
            return object : IFileWatchRequest {
                override val callerKey: String = key
                override val watchDirectory: String = directory
                override val isRecursive: Boolean = recursive
            }
        }
    }
}