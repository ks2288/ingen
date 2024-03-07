@file:OptIn(ExperimentalUnsignedTypes::class)

package net.il.data

import data.IOPacket
import data.IOPacket.Companion.PAYLOAD_MAX_SIZE
import org.jetbrains.annotations.VisibleForTesting
import util.CRC16Utils
import kotlin.math.ceil

object IOProcessor {
    /**
     * Builds a list of [IOPacket] instances from an aggregate set of [UByte]
     * values
     *
     * @param processId unique identifier of the subprocess
     * @param data array of all uByte data to be sent
     * @param sliceSize size of each slice (packet payload)
     * @return list of [IOPacket] objects to be sent
     */
    fun buildPackets(
        processId: Int,
        data: UByteArray,
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
                        crc = p.second.toInt(),
                        type = PacketType.DATA.ordinal,
                        index = i + 1,
                        parts = sliced.size,
                        payload = p.first
                    )
                )
            }
            this
        }
    }

    /**
     * Takes a uByte array, and generates CRCs per a given slice count,
     * returned with a provided size
     *
     * @param data full block of uByte data to slice and CRC
     * @param sliceSize maximum size of each payload slice
     * @return list containing pairs of data slices and their CRC16 hashes
     */
    @VisibleForTesting
    fun sliceData(
        data: UByteArray,
        sliceSize: Int = PAYLOAD_MAX_SIZE
    ): List<Pair<UByteArray, UShort>> {
        return with(arrayListOf<Pair<UByteArray, UShort>>()) {
            var remaining = data.size
            val count = ceil(data.size.toFloat() / sliceSize).toInt()
            for (i in 0 until count) {
                val start = sliceSize * i
                val size = sliceSize
                    .takeIf { sliceSize <= remaining }
                    ?: remaining
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
