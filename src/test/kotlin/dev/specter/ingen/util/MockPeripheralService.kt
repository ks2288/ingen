package dev.specter.ingen.util

import dev.specter.ingen.CommanderTest.Companion.PYTHON_PATH
import dev.specter.ingen.IPeripheralService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import java.util.*

object MockPeripheralService : IPeripheralService {
    override val tag: String get() = "MOCK_PERIPHERAL_SERVICE"
    override val key: String = UUID.randomUUID().toString()
    override val compositeDisposable: CompositeDisposable = CompositeDisposable()
    override val dataPublisher: BehaviorProcessor<Any> = BehaviorProcessor.create()
    override val path: String? get() = PYTHON_PATH
    override val launchArgs: List<String> get() = listOf(SCRIPT_PATH, PUBLISH_INTERVAL.toString())

    override fun reflect(raw: String): Any? = MockServiceData.fromString(raw = raw)

    private const val SCRIPT_PATH = "/home/specter/.source/ingen/src/test/resources/test_service.py"
    private const val PUBLISH_INTERVAL = 1
}