@file:OptIn(ExperimentalUnsignedTypes::class)

package util

import util.ext.shl
import util.ext.shr

/**
 * Simple utility object for working with CRC16 hash values during data
 * operations requiring integrity checks
 */
object CRC16Utils {
    /**
     * Known polynomial for CRC-16 calculations
     */
    private const val CRC16_POLY_HEX = 0x1021

    /**
     * CRC-16 lookup table
     */
    private val crc16LookupTable = (0 until 256).map {
        crc16(it.toUByte(), CRC16_POLY_HEX.toUShort())
    }

    /**
     * Calculates a CRC-16 given an unsigned byte and a provided polynomial
     *
     * @param uByte unsigned byte input
     * @param polynomial 2-byte polynomial for CRC-16 calculation
     * @return unsigned 2-byte CRC-16 value
     */
    fun crc16(
        uByte: UByte,
        polynomial: UShort = CRC16_POLY_HEX.toUShort()
    ): UShort {
        val bigEndian = uByte.toUShort() shl 8
        return (0 until 8).fold(bigEndian) { result, _ ->
            val isLittleEndian =
                result and 0x8000.toUShort() != 0.toUShort()
            val shifted = result shl 1
            when (isLittleEndian) {
                true -> shifted xor polynomial
                false -> shifted
            }
        }
    }

    /**
     * Calculates a CRC-16 from a UByte array input
     *
     * @param uBytes full UByte array of the input data
     * @return unsigned 2-byte CRC-16 value
     */
    fun crc16(uBytes: UByteArray): UShort {
        return uBytes.fold(0.toUShort()) { remainder, byte ->
            val bigEndian = byte.toUShort() shl 8
            val pos = (bigEndian xor remainder) shr 8
            crc16LookupTable[pos.toInt()] xor (remainder shl 8)
        }
    }

    /**
     * Calculates a CRC-16 from a byte array input
     *
     * @param bytes full byte array of the input data
     * @return unsigned 2-byte CRC-16 value
     */
    fun crc16(bytes: ByteArray): UShort =
        crc16(bytes.map(Byte::toUByte).toUByteArray())

    /**
     * Calculates a CRC-16 from a UByte array input, given a known initial value
     *
     * @param uBytes full UByte array of the input data
     * @param initial known initial value when compounding CRC values per slice
     * @return unsigned 2-byte CRC-16 value
     */
    fun crc16(uBytes: UByteArray, initial: UShort = 0.toUShort()): UShort {
        return uBytes.fold(initial) { r, b ->
            val bigEndian = b.toUShort() shl 8
            val pos = (bigEndian xor r) shr 8
            crc16LookupTable[pos.toInt()] xor (r shl 8)
        }
    }

    /**
     * Calculates a CRC-16 from a byte array input, given a known initial value
     *
     * @param bytes full byte array of the input data
     * @param initial known initial value when compounding CRC values per slice
     * @return unsigned 2-byte CRC-16 value
     */
    fun crc16(bytes: ByteArray, initial: Short = 0): UShort =
        crc16(bytes.map(Byte::toUByte).toUByteArray(), initial.toUShort())
}
