@file:OptIn(InternalCoroutinesApi::class)

package dev.specter.ingen

import dev.specter.ingen.config.ConfigBuilder
import dev.specter.ingen.config.IngenConfig
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.InternalCoroutinesApi

interface ICommandService {
    val config: IngenConfig
    val commands: List<Command>
    val commander: Commander
    val disposableMap: MutableMap<String, CompositeDisposable>

    fun execute(
        key: String,
        code: Int,
        args: List<String>,
        threaded: Boolean = false
    ): String
    fun executeAsync(code: Int, args: List<String>, threaded: Boolean = false): Flowable<Any>
    fun executeExplicit(
        path: String,
        args: List<String>,
        environmentVars: Map<String, String>,
        type: ProcessType
    ): String
    fun executeExplicitAsync(
        path: String,
        args: List<String>,
        environmentVars: Map<String, String>,
        type: ProcessType
    ): Flowable<Any>
    fun watchFiles(directory: String, recursive: Boolean = true)
    fun destroy(pid: String)
    fun destroyAll()
    fun teardown()
}

object CommandService : ICommandService {
    override val config: IngenConfig = ConfigBuilder.buildConfig()
    override val commands: List<Command> = ConfigBuilder.buildCommands() ?: listOf()
    // TODO: pass a config property, not the whole thing
    override val commander: Commander = Commander(configuration = ConfigBuilder.buildConfig())
    override val disposableMap: MutableMap<String, CompositeDisposable> = mutableMapOf()

    override fun execute(
        key: String,
        code: Int,
        args: List<String>,
        threaded: Boolean
    ): String {
        TODO()
//        val sp = Subprocess(
//            callerKey = key
//        )
    }

    override fun executeAsync(
        code: Int,
        args: List<String>,
        threaded: Boolean
    ): Flowable<Any> {
        TODO()
    }

    override fun executeExplicit(
        path: String,
        args: List<String>,
        environmentVars: Map<String, String>,
        type: ProcessType
    ): String {
        TODO("Not yet implemented")
    }

    override fun executeExplicitAsync(
        path: String,
        args: List<String>,
        environmentVars: Map<String, String>,
        type: ProcessType
    ): Flowable<Any> {
        TODO("Not yet implemented")
    }

    override fun watchFiles(directory: String, recursive: Boolean) {
        TODO("Not yet implemented")
    }

    override fun destroy(pid: String) {
        TODO("Not yet implemented")
    }

    override fun destroyAll() {
        TODO("Not yet implemented")
    }

    override fun teardown() {
        TODO("Not yet implemented")
    }
}