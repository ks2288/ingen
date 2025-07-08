@file:OptIn(InternalCoroutinesApi::class)

package dev.specter.ingen

import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.InternalCoroutinesApi

interface ICommandService {
    val commander: Commander
    val disposableMap: MutableMap<String, CompositeDisposable>

    fun execute(threaded: Boolean = false): String
    fun watchFiles(directory: String, recursive: Boolean = true)
    fun destroy(pid: String)
    fun destroyAll()
    fun teardown()
}



object CommandService : ICommandService {
    override val commander: Commander = Commander()
    override val disposableMap: MutableMap<String, CompositeDisposable> = mutableMapOf()

    override fun execute(threaded: Boolean): String {
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