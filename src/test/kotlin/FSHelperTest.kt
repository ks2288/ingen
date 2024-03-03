import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import net.il.IngenConfig
import net.il.util.FSHelper

class FSHelperTest {
    private var config = IngenConfig()
    private var testDirectoryPath = config.runtimeDirectory + TEST_DIR
    private lateinit var testDirectory: File
    @Before
    fun setup() {
        testDirectory = File(testDirectoryPath)
    }

    @After
    fun teardown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun testCreatePathWrapper() {
        val success = FSHelper.createPathDirectories(testDirectoryPath)
        assert(success)
        // second assertion ensures the File instance carries the updated path creation, and consequently exists
        assert(testDirectory.exists())
    }

    @Test
    fun testRecursiveCopyWrapper() {

    }

    companion object {
        const val TEST_DIR = "/.ingen_test"
    }
}