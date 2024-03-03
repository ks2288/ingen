@file:OptIn(ExperimentalUnsignedTypes::class)

import data.IOData
import org.junit.After
import org.junit.Before
import org.junit.Test
import util.TestConstants
import java.util.*
import kotlin.test.assertEquals

class IODataTest {
    @Before
    fun setup() {}

    @After
    fun teardown() {}

    @Test
    fun testSliceData() {
        val sliced = TEST_IO_DATA.sliceData()
        val expected = TEST_IO_DATA.sliceData(data = TEST_PAYLOAD)
        sliced.forEachIndexed { i, p ->
            assert(p.second == expected[i].second)
        }
    }

    @Test
    fun testSliceSizeVariance() {
        val sliced = TEST_IO_DATA.sliceData(sliceSize = TEST_SLICE_SIZE)
        val expected = TEST_IO_DATA.sliceData(
            data = TEST_PAYLOAD,
            sliceSize = TEST_SLICE_SIZE
        )
        sliced.forEachIndexed { i, p ->
            assert(p.second == expected[i].second)
        }
    }

    @Test
    fun testBuildPackets() {
        val packets = TEST_IO_DATA.buildPackets()
        val expected = TEST_IO_DATA.buildPackets(data = TEST_PAYLOAD)
        packets.forEachIndexed { i, p ->
            assertEquals(
                listOf(
                    p.processId,
                    p.crc.toInt(),
                    p.payload.size,
                    p.index
                ),
                listOf(
                    expected[i].processId,
                    expected[i].crc.toInt(),
                    expected[i].payload.size,
                    expected[i].index
                )
            )
        }
    }

    companion object {
        const val TEST_SLICE_SIZE = 8
        // 128 ubyte payload data
        private val TEST_PAYLOAD: UByteArray
            get() {
                val reversedData = TestConstants.TEST_PACKET_DATA.reversed().toUByteArray()
                return with(arrayListOf<UByte>()) {
                    // add one from the reversed copy, and one from the original back-to-back for arbitrary variance
                    reversedData.forEachIndexed { i, b ->
                        add(b)
                        add(TestConstants.TEST_PACKET_DATA[i])
                    }
                    this.toUByteArray()
                }
            }

        // test IOData with a 128 uByte payload
        private val TEST_IO_DATA: IOData = IOData(
            processId = UUID.randomUUID().toString(),
            byteData = TEST_PAYLOAD
        )
    }
}