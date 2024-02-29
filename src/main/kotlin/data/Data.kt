@file:OptIn(ExperimentalUnsignedTypes::class)

package data

import data.IOPacket.Companion.PAYLOAD_MAX_SIZE
import org.jetbrains.annotations.VisibleForTesting
import util.CRC16Utils
import kotlin.math.ceil

enum class PacketType {
    TRANSMIT_BEGIN,
    TRANSMIT_END,
    ACK,
    ERROR,
    DATA
}

/**
 * Class for housing an individual data transaction's-worth of information, expected to be sent to a receiver in
 * multi-segment fashion
 */
class IOData(
    private val processId: String,
    private val byteData: UByteArray
) {
    /**
     *
     */
    fun buildPackets(
        data: UByteArray = byteData,
        sliceSize: Int = PAYLOAD_MAX_SIZE
    ): List<IOPacket> {
        return with(arrayListOf<IOPacket>()) {
            val sliced = sliceData(
                data = data,
                sliceSize = sliceSize
            )
            sliced.forEachIndexed { i, p ->
                add(
                    IOPacket(
                        processId = processId,
                        crc = p.second,
                        index = i + 1,
                        payload = p.first
                    )
                )
            }
            this
        }
    }
    /**
     * Takes a uByte array, and generates CRCs per a given slice count, formulated per a provided size
     *
     * @param data full block of uByte data to slice and CRC
     * @param sliceSize maximum size of each slice, each of which become packet payloads
     * @return list containing pairs of data slices and their corresponding CRC16 values
     */
    @VisibleForTesting
    fun sliceData(
        data: UByteArray = byteData,
        sliceSize: Int = PAYLOAD_MAX_SIZE
    ): List<Pair<UByteArray, UShort>> {
        return with(arrayListOf<Pair<UByteArray, UShort>>()) {
            var remaining = data.size
            val count = ceil(data.size.toFloat() / sliceSize).toInt()
            for (i in 0 until count) {
                val start = sliceSize * i
                val size = sliceSize.takeIf { sliceSize <= remaining } ?: remaining
                remaining -= sliceSize
                // offset slice size by -1 for end-inclusive int range
                val end = start + size - 1
                val slice = data.slice(IntRange(start, end))
                val crc = CRC16Utils.crc16(uBytes = slice.toUByteArray())
                add(i, Pair(slice.toUByteArray(), crc))
            }
            this
        }
    }
}

data class IOPacket(
    val processId: String,
    val crc: UShort,
    val index: Int,
    val payload: UByteArray
) {
    fun toUBytes(): UByteArray {
        TODO()
    }

    companion object {
        const val PROC_ID_OFFSET = 0
        const val PROC_ID_SIZE = 2
        const val CRC_OFFSET = 2
        const val CRC_SIZE = 2
        const val SEQUENCE_NO_OFFSET = 4
        const val SEQUENCE_NO_SIZE = 2
        const val PACKET_TYPE_OFFSET = 6
        const val PACKET_TYPE_SIZE = 1
        const val PAYLOAD_OFFSET = 7
        const val PAYLOAD_MAX_SIZE = 64
    }
}