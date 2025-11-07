package dev.specter.ingen.util

import dev.specter.ingen.CommanderTest
import dev.specter.ingen.CommanderTest.Companion.PYTHON_PATH
import dev.specter.ingen.IPeripheralService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import java.util.*

object MockInteractiveService : IPeripheralService {
    override val tag: String get() = "MOCK_INTERACTIVE_SERVICE"
    override val key: String = UUID.randomUUID().toString()
    override val compositeDisposable: CompositeDisposable = CompositeDisposable()
    override val dataPublisher: BehaviorProcessor<Any> = BehaviorProcessor.create()
    override val inputProcessor: BehaviorProcessor<String> = BehaviorProcessor.create()
    override val path: String get() = PYTHON_PATH
    override val launchArgs: List<String> get() = listOf(CommanderTest.INTERACTIVE_MODULE_PATH)
    override fun reflect(raw: String): Any = raw
}