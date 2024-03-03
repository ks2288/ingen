@file:OptIn(ExperimentalUnsignedTypes::class)

import org.junit.After
import org.junit.Before
import org.junit.Test
import util.CRC16Utils
import util.TestConstants.TEST_PACKET_DATA
import util.TestConstants.TEST_UBYTE
import util.TestConstants.TEST_UBYTE_ARRAY_CRC16
import util.TestConstants.TEST_UBYTE_CRC16

class CRC16UtilsTest {
    @Before
    fun setup() {}

    @After
    fun teardown() {}

    @Test
    fun testUByteCrc16() {
        val crc = CRC16Utils.crc16(TEST_UBYTE)
        assert(crc == TEST_UBYTE_CRC16)
    }

    @Test
    fun testUByteArrayCrc16() {
        val crc = CRC16Utils.crc16(uBytes = TEST_PACKET_DATA)
        assert(crc == TEST_UBYTE_ARRAY_CRC16)
    }

    @Test
    fun testMapBytesForCrc16() {
        val crc = CRC16Utils.crc16(bytes = TEST_PACKET_DATA.toByteArray())
        assert(crc == TEST_UBYTE_ARRAY_CRC16)
    }

    @Test
    fun testUByteArrayCrcBySlice() {
        val sliceSize = 8
        var crc: UShort = 0.toUShort()
        for (i in 0 until 8) {

            val sliceStart = i * sliceSize
            // offset slice size by -1 for end-inclusive int range
            val sliceEnd = sliceStart + sliceSize - 1
            val slice = TEST_PACKET_DATA.slice(IntRange(sliceStart, sliceEnd))
            val sliceCrc = CRC16Utils.crc16(uBytes = slice.toUByteArray(), initial = crc)
            crc = sliceCrc
        }
        assert(crc == TEST_UBYTE_ARRAY_CRC16)
    }

    @Test
    fun testMapByteArrayCrcBySlice() {
        val sliceSize = 8
        val mapped = TEST_PACKET_DATA.toByteArray()
        var crc: UShort = 0.toUShort()
        for (i in 0 until 8) {

            val sliceStart = i * sliceSize
            // offset slice size by -1 for end-inclusive int range
            val sliceEnd = sliceStart + sliceSize - 1
            val slice = mapped.slice(IntRange(sliceStart, sliceEnd)).toByteArray()
            val sliceCrc = CRC16Utils.crc16(bytes = slice, initial = crc.toShort())
            crc = sliceCrc
        }
        assert(crc == TEST_UBYTE_ARRAY_CRC16)
    }
}