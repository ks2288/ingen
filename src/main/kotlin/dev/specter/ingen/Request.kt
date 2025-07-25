package dev.specter.ingen

import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.channels.Channel

/// Route "package" for all async ops, including mandatory output publisher and optional input publisher
typealias IORoute = Pair<BehaviorProcessor<String>, BehaviorProcessor<String>?>
/// Route "package" for all file watcher launches
typealias IORouteFW = Pair<BehaviorProcessor<String>, Channel<Int>>

interface IExecRequest {
    val callerKey: String
    val subprocessUID: Int
    val userArgs: List<String>
    val envVars: Map<String, String>

    companion object {
        fun create(
            key: String,
            uid: Int,
            args: List<String> = listOf(),
            env: Map<String, String> = mapOf()
        ) : IExecRequest {
            return object: IExecRequest {
                override val callerKey: String = key
                override val subprocessUID: Int = uid
                override val userArgs: List<String> = args
                override val envVars: Map<String, String> = env
            }
        }
    }
}

interface IExecRequestExplicit {
    val callerKey: String
    val programPath: String
    val workingDir: String
    val userArgs: List<String>
    val envVars: Map<String, String>

    companion object {
        fun create(
            key: String,
            path: String,
            directory: String,
            args: List<String> = listOf(),
            env: Map<String, String> = mapOf()
        ) : IExecRequestExplicit {
            return object : IExecRequestExplicit {
                override val callerKey: String = key
                override val programPath: String = path
                override val workingDir: String = directory
                override val userArgs: List<String> = args
                override val envVars: Map<String, String> = env
            }
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