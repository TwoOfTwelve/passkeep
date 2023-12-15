import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.posix.exit
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
val socketPath by lazy {
    val user = getenv("USER")!!.toKString()
    "/tmp/keePassXcFrontendSocket-$user"
}

suspend fun handleConnection(args: Array<String>) {
    val connection = openConnection()

    val socketIO = SocketIO(connection)
    socketIO.writeUtf8Line(args.joinToString(" "))

    var done = false
    while (!done) {
        val command = socketIO.readCommand()

        when(command.type) {
            CommandType.READ -> socketIO.writeUtf8Line(readText(command.param))
            CommandType.WRITE -> println(command.param)
            CommandType.PASSWORD -> socketIO.writeUtf8Line(readPw(command.param))
            CommandType.DONE -> done = true
        }
    }

    connection.close()
}

private suspend fun openConnection(): Socket {
    val selectorManager = SelectorManager(Dispatchers.IO)
    return try {
        aSocket(selectorManager).tcp().connect(UnixSocketAddress(socketPath))
    } catch (_: IllegalStateException) {
        println("Daemon is not running")
        exit(1)
        null
    }!!
}

class SocketIO(private val read: ByteReadChannel, private val write: ByteWriteChannel) {
    constructor(connection: Socket) : this(connection.openReadChannel(), connection.openWriteChannel(true))

    suspend fun readUtf8Line(): String {
        return read.readUTF8Line() ?: ""
    }

    suspend fun writeUtf8Line(line: String) {
        write.writeStringUtf8(line)
        if (!line.endsWith("\n")) {
            write.writeStringUtf8("\n")
        }
    }

    suspend fun readCommand(): Command {
        val line = readUtf8Line()

        var splitIndex = line.indexOf(' ')
        if (splitIndex == -1) {
            splitIndex = line.length
        }

        val cmd = line.substring(0, splitIndex)
        val rest = if (splitIndex < line.length) {
            line.substring(splitIndex + 1)
        } else {
            ""
        }

        return Command(CommandType.byCommandName(cmd), rest)
    }
}

enum class CommandType(val commandName: String) {
    READ("in"), WRITE("out"), PASSWORD("pw"), DONE("done");

    companion object {
        fun byCommandName(commandName: String): CommandType {
            return entries.first { it.commandName == commandName }
        }
    }
}

data class Command(val type: CommandType, val param: String)