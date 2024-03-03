@file:OptIn(ExperimentalSerializationApi::class)

package message

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import net.il.util.Logger

/**
 * Simple utility object for leveraging the KotlinX serialization library to turn decodable objects into class instances
 */
// TODO: look into combining one or all of these methods using a case-based switch of object type
object SerializationHandler {
    // region Properties

    /**
     * Compartmentalized, private [Json] instance via the JsonBuilder
     */
    private val _json = Json {
        isLenient = true
        allowStructuredMapKeys = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    /**
     * Public accessor to allow for property protection along with access for reified
     * function parameters
     */
    val json = _json

    // endregion

    // region Public Methods

    /**
     * Simply parses an operational JSON array from a string source
     *
     * @param source string to be parsed/decoded
     * @return parsed JSON array, or null on fail
     */
    fun parseJsonArrayFromString(source: String): JsonArray? = try {
        _json.decodeFromString(source)
    }
    catch (e: Exception) {
        Logger.error(e)
        null
    }

    //endregion

    //region Generics

    /**
     * Parses a serializable object from a source string
     *
     * @param T type of serializable class instance to be decoded
     * @param source string to be parsed/decoded
     * @return serializable class's instance
     */
    inline fun <reified T : Any> serializableFromString(source: String): T? = try {
        json.decodeFromString<T>(source)
    } catch (e: Exception) {
        Logger.error(e)
        null
    }

    /**
     * Parses a serializable class instance from a source JSON object
     *
     * @param T type of serializable class instance to be decoded
     * @param source source JSON object to be decoded into a class instance
     * @return serializable class's instance
     */
    inline fun <reified T : Any> serializableFromObject(source: JsonObject): T? = try {
        val p = source.jsonPrimitive.toString()
        serializableFromString<T>(p)
    } catch (e: Exception) {
        Logger.error(e)
        null
    }

    /**
     * Parses a serializable object from a source JSON element object
     *
     * @param T type of serializable class instance to be decoded
     * @param source JSON element to be decoded/parsed
     * @return serializable class's instance
     */
    inline fun <reified T : Any> serializableFromElement(source: JsonElement): T? = try {
        json.decodeFromJsonElement<T>(source)
    } catch (e: Exception) {
        Logger.error(e)
        null
    }

    /**
     * Decodes a list of serializable objects from a source string
     *
     * @param T type of serializable class instance to be decoded
     * @param source string to be parsed/decoded
     * @return list of parsed objects to be cast further as needed by caller
     */
    inline fun <reified T : Any> serializableListFromString(source: String): List<T>? = try {
        with(arrayListOf<T>()) {
            val array: JsonArray = json.decodeFromString(source)
            array.forEach {
                val string = json.encodeToString(it)
                val decoded = json.decodeFromString<T>(string)
                add(decoded)
            }
            this
        }
    } catch (e: Exception) {
        Logger.error(e)
        null
    }

    //endregion
}
