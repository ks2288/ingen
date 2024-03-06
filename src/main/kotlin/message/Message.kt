package message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    @SerialName("route")
    val routeCode: Int,
    @SerialName("type")
    val typeCode: Int,
    override val content: String
) : Signal {
    override val route: SignalRoute
        get() = SignalRoute.entries.first {it.routeCode == routeCode}

    override val type: SignalType
        get() = SignalType.entries.first { it.typeCode == typeCode }
}

interface Signal {
    val route: SignalRoute
    val type: SignalType
    val content: String
}

interface MessageContent {
    val executablePathCode: Int
    val executableDirectoryCode: Int
    val executableContent: String
}

@Serializable
data class CommandContent(
    override val executablePathCode: Int,
    override val executableDirectoryCode: Int,
    override val executableContent: String
) : MessageContent

enum class SignalType {
    ACK {
        override val typeCode: Int
            get() = 0
    },
    ERROR {
        override val typeCode: Int
            get() = 1
    },
    TIMEOUT {
        override val typeCode: Int
            get() = 2
    },
    CMD_INIT {
        override val typeCode: Int
            get() = 3
    },
    CMD_KILL {
        override val typeCode: Int
            get() = 4
    };
    abstract val typeCode: Int
}

enum class SignalRoute {
    SUBPROC {
        override val routeCode: Int
            get() = 1
    },
    BLE {
        override val routeCode: Int
            get() = 2
    },
    WEB {
        override val routeCode: Int
            get() = 3
    },
    UI {
        override val routeCode: Int
            get() = 0
    };

    abstract val routeCode: Int
}
