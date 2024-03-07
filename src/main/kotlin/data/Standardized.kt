@file:OptIn(ExperimentalUnsignedTypes::class)

package net.il.data

/**
 * Contract for all data packets being sent to and from sender and receiver
 * modules when standardized communications are necessary for ensuring data
 * integrity during runtime
 *
 * @property processId unique ID of the sand-boxed subprocess
 * @property crc 2-byte CRC16 hash value of the [payload]
 * @property type ordinal value of [PacketType]
 * @property index index within multipart set (1 for single-packet messages)
 * @property parts total number of message parts
 * @property payload arbitrary payload data to be parsed per project needs
 */
interface StandardizedProtocol {
    val processId: Int
    val crc: Int
    val type: Int
    val index: Int
    val parts: Int
    val payload: UByteArray

    /**
     * Takes each property and reflects them into their [UByteArray]
     * representations as an aggregate set
     *
     * @return [UByteArray] containing [UByte] values for all properties
     */
    fun toUBytes(): UByteArray
}

/**
 * Distinctions for all possible packet types being sent to and from sender
 * and receiver modules when [StandardizedProtocol] is implemented
 *
 * @property MP_INIT signals the start of a multipart message (payload = parts)
 * @property MP_TERM signals the end of a multipart message (payload = none)
 * @property ACK signals acknowledgement from a receiver to a sender
 * @property ERR signals that an error has occurred
 * @property DATA contains the data of a message (both multi and single-part)
 */
enum class PacketType {
    MP_INIT,
    MP_TERM,
    ACK,
    ERR,
    DATA;

    /**
     * Convenience accessor value for ordinal's [UByte] representation
     */
    val ordinalUByte: UByte
        get() = ordinal.toUByte()
}