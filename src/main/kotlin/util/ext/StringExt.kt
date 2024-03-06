package util.ext

import net.il.util.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Helper extension for quickly turning formatted strings into [LocalDateTime]
 * instances
 *
 * @param formatString format provided to [SimpleDateFormat] as a string
 * @return reflected [LocalDateTime], or null on failure
 */
fun String.toLocalDateTime(formatString: String): LocalDateTime? = try {
    LocalDateTime.parse(
        this,
        DateTimeFormatter
            .ofPattern(formatString)
    )
} catch (e: Exception) {
    Logger.error(e.localizedMessage)
    null
}

/**
 * Helper extension for quickly turning formatted strings into [Date] instances
 *
 * @param formatString format provided to [SimpleDateFormat] as a string
 * @return reflected [Date], or null on failure
 */
fun String.toJavaDate(formatString: String): Date? = try {
    SimpleDateFormat(formatString, Locale.getDefault())
        .parse(this)
} catch (e: Exception) {
    Logger.error(e.localizedMessage)
    null
}
