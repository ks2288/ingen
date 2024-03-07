@file:OptIn(ExperimentalUnsignedTypes::class)

package data

import net.il.data.StandardizedProtocol
import net.il.util.ext.toUByteArray

/**
 * Concrete implementation of [StandardizedProtocol] for all packets being
 * sent to and from an Ingen instance and a sand-boxed subprocess
 */
data class IOPacket(
    override val processId: Int,
    override val crc: Int,
    override val type: Int,
    override val index: Int,
    override val parts: Int,
    override val payload: UByteArray
) : StandardizedProtocol {
    /**
     * Concrete implementation of [StandardizedProtocol.toUBytes]
     */
    override fun toUBytes(): UByteArray = with(arrayListOf<UByte>()) {
        addAll(processId.toUByteArray(size = PID_SIZE))
        addAll(crc.toUByteArray(size = CRC_SIZE))
        addAll(type.toUByteArray(size = PACKET_TYPE_SIZE))
        addAll(index.toUByteArray(size = INDEX_SIZE))
        addAll(parts.toUByteArray(size = PARTS_SIZE))
        addAll(payload)
        this.toUByteArray()
    }

    companion object {
        const val PID_OFFSET = 0
        const val PID_SIZE = 2
        const val CRC_OFFSET = 2
        const val CRC_SIZE = 2
        const val PACKET_TYPE_OFFSET = 4
        const val PACKET_TYPE_SIZE = 2
        const val INDEX_OFFSET = 6
        const val INDEX_SIZE = 3
        const val PARTS_OFFSET = 9
        const val PARTS_SIZE = 3
        const val PAYLOAD_OFFSET = 12
        const val PAYLOAD_MAX_SIZE = 64
    }
}
