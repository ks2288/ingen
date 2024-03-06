import command.ISubprocess
import command.Subprocess
import kotlinx.serialization.json.*
import message.SerializationHandler
import org.junit.After
import org.junit.Before
import org.junit.Test
import util.TestConstants
import java.io.File

class SerializationTest {
    private lateinit var jsonArrayString: String
    private lateinit var cmdArrayString: String
    private lateinit var jsonObjString: String
    private var testArray1: JsonArray? = null
    private var testArray2: JsonArray? = null
    private var testElement: JsonElement? = null
    private var testObject: JsonObject? = null
    private var objectList: ArrayList<JsonElement> = arrayListOf()
    private val execList = arrayListOf<ISubprocess>()

    @Before
    fun setup() {
        jsonArrayString = File(JSON_ARRAY_FILE_PATH).readText()
        jsonObjString = File(JSON_OBJ_FILE_PATH).readText()
        cmdArrayString = File(CMD_ARRAY_FILE_PATH).readText()
        testArray1 = SerializationHandler
            .parseJsonArrayFromString(jsonArrayString)
        testArray2 = SerializationHandler
            .parseJsonArrayFromString(cmdArrayString)
        testArray1?.let {
            testElement = it.first()
            testObject = testElement?.jsonObject
        }
        testElement?.jsonObject?.values?.let { objectList.addAll(it) }
        testArray2?.forEach { e ->
            SerializationHandler
                .serializableFromElement<Subprocess>(e)?.let { c ->
                    execList.add(c)
                }
        }
    }

    @After
    fun teardown() {}

    @Test
    fun test_parse_array() { assert(testArray1?.size == 2) }

    @Test
    fun test_parse_element() {
        assert(testElement?.jsonObject?.size == EXPECTED_OBJECT_SIZE)
    }

    @Test
    fun test_parse_keys() {
        assert(
            with(testObject) {
                this?.containsKey(STRING_KEY) == true
                        && this.containsKey(INT_KEY)
                        && this.containsKey(BOOL_KEY)
            }
        )
    }

    @Test
    fun test_parse_string() {
        val stringValue = objectList[0].jsonPrimitive.contentOrNull
        assert(stringValue == STRING_1_VAL)
    }

    @Test
    fun test_parse_int() {
        val intValue = objectList[1].jsonPrimitive.intOrNull
        assert(intValue == INT_1_VAL)
    }

    @Test
    fun test_parse_boolean() {
        val boolValue = objectList[2].jsonPrimitive.booleanOrNull
        assert(boolValue == BOOL_1_VAL)
    }

    @Test
    fun test_parse_executable() {
        assert(execList.size == EXPECTED_CMD_LIST_SIZE)
    }

    companion object {
        private const val TEST_ARRAY_FILE_NAME = "test_array.json"
        private const val CMD_ARRAY_FILE_NAME = "test_cmd.json"
        private const val JSON_OBJECT_FILE_NAME = "test_obj.json"
        private const val EXPECTED_CMD_LIST_SIZE = 8
        private val JSON_ARRAY_FILE_PATH =
            "${TestConstants.TEST_RES_DIR}/$TEST_ARRAY_FILE_NAME"
        private val CMD_ARRAY_FILE_PATH =
            "${TestConstants.TEST_RES_DIR}/$CMD_ARRAY_FILE_NAME"
        private val JSON_OBJ_FILE_PATH =
            "${TestConstants.TEST_RES_DIR}/$JSON_OBJECT_FILE_NAME"
        private const val EXPECTED_OBJECT_SIZE = 3
        private const val STRING_KEY = "stringKey"
        private const val INT_KEY = "intKey"
        private const val BOOL_KEY = "boolKey"
        private const val STRING_1_VAL = "test string 1"
        private const val INT_1_VAL = 0
        private const val BOOL_1_VAL = false
    }
}