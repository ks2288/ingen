package data

interface IMessage {
    val serviceUUID : String
    val charUUID : String

    fun mapToResponse(byteArray: ByteArray) : IMessageSegment
    fun toByteArray(): ByteArray

    fun encrypt(): Boolean = true
}

interface IMessageSegment {
    val sequenceNumber : Int
}