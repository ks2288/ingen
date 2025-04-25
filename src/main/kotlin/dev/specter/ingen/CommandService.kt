package dev.specter.ingen

import dev.specter.ingen.util.Logger
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

// simply to provide context
typealias CommandArguments = List<String>
typealias SPQueueItem = Triple<ISubprocess, CommandArguments, ProcessType>

/**
 * Provides a basis for creating classes that manage subprocess execution - specifically async -
 */
interface ICommandService {
    val commander: Commander
    val fixedPoolMap: MutableMap<String, ExecutorService>
    val cachedPoolMap: MutableMap<String, ThreadPoolExecutor>
    val inputDisposable: CompositeDisposable
    val outputDisposable: CompositeDisposable
    val inputProcessors: MutableMap<String, BehaviorProcessor<String>>
    val outputProcessors: MutableMap<String, BehaviorProcessor<String>>

    fun submit(item: SPQueueItem, isCached: Boolean = false)
    fun submitAll(items: List<SPQueueItem>, isCached: Boolean = false)
    fun execute(item: SPQueueItem, isCached: Boolean = false)
    fun executeAll(items: List<SPQueueItem>, isCached: Boolean = false)

    fun killPool(pid: String)
    fun awaitPoolTermination(pid: String, seconds: Long)

    fun buildFixedPool(pid: String)
    fun buildCachedPool(pid: String)

    fun killAll() {
        inputDisposable.dispose()
        outputDisposable.dispose()
        fixedPoolMap.forEach { it.value.shutdownNow() }
        cachedPoolMap.forEach { it.value.shutdownNow() }
        // this really shouldn't be necessary, but just in case there are any stragglers...
        commander.killAll()
    }
}

open class CommandService : ICommandService {
    override val commander: Commander
        get() = TODO("Not yet implemented")
    override val fixedPoolMap: MutableMap<String, ExecutorService>
        get() = TODO("Not yet implemented")
    override val cachedPoolMap: MutableMap<String, ThreadPoolExecutor>
        get() = TODO("Not yet implemented")
    override val inputDisposable: CompositeDisposable
        get() = TODO("Not yet implemented")
    override val outputDisposable: CompositeDisposable
        get() = TODO("Not yet implemented")
    override val inputProcessors: MutableMap<String, BehaviorProcessor<String>>
        get() = TODO("Not yet implemented")
    override val outputProcessors: MutableMap<String, BehaviorProcessor<String>>
        get() = TODO("Not yet implemented")

    override fun submit(item: SPQueueItem, isCached: Boolean) {
        TODO("Not yet implemented")
    }

    override fun submitAll(items: List<SPQueueItem>, isCached: Boolean) {
        TODO("Not yet implemented")
    }

    override fun execute(item: SPQueueItem, isCached: Boolean) {
        TODO("Not yet implemented")
    }

    override fun executeAll(items: List<SPQueueItem>, isCached: Boolean) {
        TODO("Not yet implemented")
    }

    override fun killPool(pid: String) {
        TODO("Not yet implemented")
    }

    override fun awaitPoolTermination(pid: String, seconds: Long) {
        TODO("Not yet implemented")
    }

    override fun buildFixedPool(pid: String) {
        TODO("Not yet implemented")
    }

    override fun buildCachedPool(pid: String) {
        TODO("Not yet implemented")
    }

}

object CommandServiceFactory {
    val poolMap: MutableMap<String, ExecutorService> = mutableMapOf()

    private const val CORE_POOL_SIZE = 4
    private const val MAX_POOL_SIZE = CORE_POOL_SIZE * 4
    private const val KEEP_ALIVE_TIME = 100L

    /**
     * Builds, stores (runtime), and returns a new fixed thread pool for subprocesses requiring the pooling of several
     * programs etc.
     *
     * @param pid process ID for the newly-created [ExecutorService]
     * @return newly-created [ExecutorService] for a given subprocess
     */
    fun buildSubprocessPool(pid: String): ExecutorService {
        Logger.debug("New pool created for PID: $pid")
        poolMap[pid] = Executors.newFixedThreadPool(CORE_POOL_SIZE, object : ThreadFactory {
            private val counter = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                Logger.debug("New thread created for PID: $pid")
                return Thread(null, r, "$pid-thread-${counter.incrementAndGet()}")
            }
        })
        return poolMap[pid]!!
    }

    fun executeAll(pid: String, items: List<SPQueueItem>) {
        buildList {
            repeat(items.size) {
                add(
                    Runnable {  }
                )
            }
        }
    }
}