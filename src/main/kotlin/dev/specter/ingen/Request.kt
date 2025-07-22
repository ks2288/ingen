package dev.specter.ingen

import io.reactivex.rxjava3.processors.BehaviorProcessor

typealias IORoute = Pair<BehaviorProcessor<String>, BehaviorProcessor<String>?>

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