package util.ext

import net.il.util.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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

fun String.toJavaDate(formatString: String): Date? = try {
    SimpleDateFormat(formatString, Locale.getDefault())
        .parse(this)
} catch (e: Exception) {
    Logger.error(e.localizedMessage)
    null
}