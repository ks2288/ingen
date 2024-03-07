@file:OptIn(ExperimentalUnsignedTypes::class)

import net.il.data.IOProcessor
import org.junit.After
import org.junit.Before
import org.junit.Test
import util.TestConstants

class IODataTest {
    @Before
    fun setup() {}

    @After
    fun teardown() {}

    @Test
    fun testSliceData() {
        val expected = IOProcessor.sliceData(data = TEST_PAYLOAD)
        assert(expected.size == 2 && expected.first().first.size == 64)
    }

    @Test
    fun testSliceSizeVariance() {
        val expected = IOProcessor.sliceData(
            data = TEST_PAYLOAD,
            sliceSize = TEST_SLICE_SIZE
        )
        assert(expected.size == 16)
    }

    @Test
    fun testBuildMultipart() {
        val packets = IOProcessor.buildPackets(
            processId = 0,
            data = TEST_PAYLOAD
        )
        assert(packets.size == EXPECTED_MP_MESSAGE_SIZE)
        packets.forEachIndexed { i, p ->
            assert(p.index == i + 1 && p.parts == packets.size)
        }
    }

    @Test
    fun testBuildSinglePart() {
        val packets = IOProcessor.buildPackets(
            processId = 1,
            data = TestConstants.TEST_PACKET_DATA
        )
        assert(packets.size == 1)
        packets.forEachIndexed { i, p ->
            assert(p.index == i + 1 && p.parts == packets.size)
        }
    }

    companion object {
        private const val TEST_SLICE_SIZE = 8
        private const val EXPECTED_MP_MESSAGE_SIZE = 2
        // 128 ubyte payload data
        private val TEST_PAYLOAD: UByteArray
            get() {
                val reversedData = TestConstants.TEST_PACKET_DATA
                    .reversed()
                    .toUByteArray()
                return with(arrayListOf<UByte>()) {
                    // add one from the reversed copy, and one from the original
                    // back-to-back for arbitrary variance
                    reversedData.forEachIndexed { i, b ->
                        add(b)
                        add(TestConstants.TEST_PACKET_DATA[i])
                    }
                    this.toUByteArray()
                }
            }
    }
}
