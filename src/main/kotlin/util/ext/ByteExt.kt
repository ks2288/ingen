package net.il.util.ext

import java.nio.ByteBuffer
import java.nio.ByteOrder

//region Boolean extensions

/***
 * Converts a [Boolean] to byte. [bitIndex] is left to right
 * i.e. true, [bitIndex] 0 -> 0b10000000
 *      true, [bitIndex] 7 -> 0b00000001
 *
 * @return full [Byte] representation of the given [Boolean]
 */
fun Boolean.toByte(bitIndex: Int = 7): Byte {
    return (if (this) 1 else 0).shl(7 - bitIndex).toByte()
}

//endregion

//region BooleanArray extensions

/***
 * Given a Boolean Array converts it to byte.
 * Assumes first index is correspondent to the 0 index of the byte
 * i.e.
 * ( true, false, false, true, true )
 * will be
 * 0b10011000
 * @return [Byte] representation of an 8-bit-or-less [BooleanArray]
 */
fun BooleanArray.toByte(): Byte {
    if (this.size > 8) {
        throw Exception()
    }

    var byteValue = 0
    for (index in this.indices) {
        byteValue += this[index]
            .toByte()
            .toInt()
            .shl(7 - index)
    }
    return byteValue.toByte()
}

/**
 * Converts [BooleanArray] to a [ByteArray] where each byte represents 8
 * [Boolean]s in the [BooleanArray]
 *
 * i.e.
 * Booleans 0...7   -> 0th Byte
 * Booleans 8..15  -> 1st Byte
 * .
 * .
 * Booleans n...n+7 -> n/8th Byte
 * @return [ByteArray] representation of booleans
 */
fun BooleanArray.toByteArray(): ByteArray {
    val byteArray = MutableList<Byte>(size = 0) { 0 }
    var pos = 0
    while (pos + 8 < this.size) {
        byteArray.add(
            this.copyOfRange(fromIndex = pos, toIndex = pos + 8).toByte()
        )
        pos += 8
    }

    if (pos < this.size) {
        byteArray.add(
            this.copyOfRange(fromIndex = pos, toIndex = this.size).toByte()
        )
    }
    return byteArray.toByteArray()
}

//endregion

//region ByteArray extensions

/**
 * Takes a given [ByteArray] and returns a representative hex string
 *
 * @return hex string of the given [ByteArray]
 */
fun ByteArray.asHex(): String =
    this.toHexString(format = "%02X")
        .chunked(8)
        .joinToString(" ")
        .chunked(45)
        .joinToString("\n")

/**
 * Alternate method for taking a given [ByteArray] and returning a
 * representative hex string, with varying formats
 *
 * @param format format of calculated hex string
 * @return hex string of the given [ByteArray]
 */
fun ByteArray.toHexString(format: String = "0x%02X,"): String {
    return if (isNotEmpty()) {
        StringBuffer()
            .let { buffer ->
                this.forEach { b ->
                    buffer.append(String.format(format, b))
                }
                buffer
            }
            .toString()
            .trim(',')
    } else { "" }
}

/**
 * Turns a 4-byte-or-less [ByteArray] into its integer representation
 *
 * @return [Int] value of the given [ByteArray]
 */
fun ByteArray.toInt(): Int {
    if (this.size > 4) {
        throw Exception()
    }

    return this.foldIndexed(initial = 0) { index, acc, byte ->
        acc + byte.toInt().shl(8 * index)
    }
}

//endregion

//region Int extensions

/**
 * Reflects an [Int] into its [ByteArray] representation
 *
 * @param size size of the resulting array
 * @param byteOrder endianness when putting it into the byte array
 * @return [ByteArray] representation of the given [Int]
 */
fun Int.toByteArray(
    size: Int = 4,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
): ByteArray {
    val byteArray = ByteBuffer.allocate(4)
        .order(byteOrder)
        .putInt(this)
        .array()

    return if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
        byteArray.copyOfRange(fromIndex = 0, toIndex = size)
    } else {
        byteArray.copyOfRange(fromIndex = 4 - size, toIndex = 4)
    }

}

//endregion

//region IntArray/Array<Int> extensions

/**
 * Reflects a given typed array of integers into its [ByteArray] representation
 *
 * @return [ByteArray] representation of the given typed integer array
 */
fun Array<Int>.toByteArray(): ByteArray =
    this.foldIndexed(ByteArray(this.size)) { i, a, v ->
        a.apply { set(i, v.toByte()) }
    }

/**
 * Turns an [IntArray] of a single byte or more into a [ByteArray]
 *
 * @param size number of bytes to be represented by each int
 * @param byteOrder endianness of array to be generated
 * @return [ByteArray] representation of the given [IntArray]
 */
fun IntArray.toByteArray(
    size: Int = 4,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
): ByteArray {
    return if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
        this.foldRight(
            initial = ByteArray(0),
            operation = { i: Int, acc: ByteArray ->
                acc + i.toByteArray(size = size)
            }
        )
    } else {
        this.fold(
            initial = ByteArray(0),
            operation = { acc: ByteArray, i: Int ->
                acc + i.toByteArray(size = size)
            }
        )
    }
}

//endregion

//region Long extensions

/**
 * Turns a given [Long] into a representative [ByteArray]
 *
 * @param size size of the array to be returned
 * @param byteOrder endianness of array to be generated
 * @return [ByteArray] representation of the given [Long]
 */
fun Long.toByteArray(
    size: Int = 4,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
): ByteArray {
    val byteArray = ByteBuffer.allocate(8)
        .order(byteOrder)
        .putLong(this)
        .array()

    return if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
        byteArray.copyOfRange(fromIndex = 0, toIndex = size)
    } else {
        byteArray.copyOfRange(fromIndex = 8 - size, toIndex = 8)
    }
}

//endregion