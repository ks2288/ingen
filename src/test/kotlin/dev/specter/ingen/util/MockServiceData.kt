package dev.specter.ingen.util

/**
 * Simple data class representing information being fed to Hatter from a Raspberry Pi SenseHat;
 * instead of leveraging Kotlin's serialization framework, a much simpler method is used that
 * leverages the unprintable "group separator" (designated <GS> in printable text, and 0x1D and/or
 *  code 29 in ASCII) as a delimiter for payloads with known numbers of parts; mitigates unnecessary
 *  overhead that could come from processing larger payloads (i.e. full JSON) on Hatter nodes with
 *  minimal processing power; specs for SenseHat environmental sensors can be found here:
 *  https://sense-hat.readthedocs.io/en/latest/api/#environmental-sensors
 *
 *  @property temperature floating point temperature value, in Celsius
 *  @property pressure floating point pressure value, in Millibars
 *  @property humidity floating point humidity value, in a relative percentage
 */
data class MockServiceData(
    val temperature: Float = 0f,
    val pressure: Float = 0f,
    val humidity: Float = 0f
) {
    companion object {
        /**
         * Static reflector/constructor method for quickly instantiating a data model from a raw
         * string separated by the group separator (ASCII 0x1D/29, signified in printable text as
         * <GS>, but will only show an empty, rectangular box character when viewed as "printable")
         *
         * @param raw raw data string delimited by the group separator
         * @return instantiated data object if successful, null on parse failure
         */
        fun fromString(raw: String): MockServiceData? {
            val parts = raw.split(0x1D.toChar())
            return try {
                MockServiceData(
                    temperature = parts.first().toFloat(),
                    pressure = parts[1].toFloat(),
                    humidity = parts[2].toFloat()
                )
            } catch (e: Exception) {
                Logger.error(e)
                null
            }
        }
    }
}