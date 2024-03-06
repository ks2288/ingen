package util.ext

/**
 * Performs a left-hand shift to the 2 bytes within a UShort
 *
 * @param shiftCount number of bits to shift left
 * @return the two unsigned bytes, shifted left
 */
infix fun UShort.shl(shiftCount: Int): UShort =
    (this.toUInt() shl shiftCount).toUShort()

/**
 * Performs a right-hand shift to the 2 bytes within a UShort
 *
 * @param shiftCount number of bits to shift right
 * @return the two unsigned bytes, shifted right
 */
infix fun UShort.shr(shiftCount: Int): UShort =
    (this.toUInt() shr shiftCount).toUShort()