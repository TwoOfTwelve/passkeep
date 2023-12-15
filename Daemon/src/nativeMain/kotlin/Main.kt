import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.posix.*

lateinit var socket: ServerSocket
@OptIn(ExperimentalForeignApi::class)
val socketPath by lazy {
    val user = getenv("USER")!!.toKString()
    "/tmp/keePassXcFrontendSocket-$user"
}

@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    CliHandler.dbFile = args[0]

    runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        remove(socketPath)
        socket = aSocket(selectorManager).tcp().bind(UnixSocketAddress(socketPath))

        signal(SIGTERM, staticCFunction { _: Int ->
            socket.close()
            PwCache.stop()
            exit(0)
        })

        var running = true
        while (running) {
            val connection = socket.accept()
            launch(Dispatchers.IO) {
                try {
                    handleConnection(connection)
                } catch (e: Throwable) {
                    println("error while handling call.")
                }
            }
        }
        PwCache.stop()
    }
}

suspend fun handleConnection(connection: Socket) {
    val receiveChannel = connection.openReadChannel()
    val writeChannel = connection.openWriteChannel(autoFlush = true)

    val io = ChannelIOHandler(receiveChannel, writeChannel)

    val command = receiveChannel.readUTF8Line()!!.split(" ")
    when (command[0]) {
        "ls" -> {
            PwList.list(io).forEach {
                io.output(it)
            }
        }

        "pw" -> {
            CliHandler.copyPassword(command[1], io)
        }

        "name" -> {
            CliHandler.copyUsername(command[1], io)
        }

        "url" -> {
            CliHandler.copyUrl(command[1], io)
        }

        "reset-pw" -> {
            PwCache.reset()
        }

        "reset-ls" -> {
            PwList.reset()
        }

        "--help", "-h", "help" -> {
            io.output("Available commands:")
            io.output("")
            io.output("ls                     - Lists all passwords")
            io.output("pw   [entry path]      - Copies the password")
            io.output("name [entry path]      - Copies the username")
            io.output("url  [entry path]      - Copies the url")
            io.output("exit                   - Stops the daemon")
            io.output("")
            io.output("reset-pw               - Resets the cached password")
            io.output("reset-ls               - Resets the cached password list")
            io.output("")
            io.output("--help, -h, help       - Prints this help")
            io.output("--version, -v, version - Prints the version")
        }

        "--version", "-v", "version" -> {
            io.output("Version 1.0")
        }

        "exit" -> {
            io.done()
            connection.close()
            socket.close()
            PwCache.stop()
            exit(0)
        }
    }

    io.done()

    connection.close()
}

interface IOHandler {
    suspend fun readPassword(prompt: String): String
    suspend fun readInput(prompt: String): String
    suspend fun output(output: String)
    suspend fun done()
}

private class ChannelIOHandler(val read: ByteReadChannel, val write: ByteWriteChannel) : IOHandler {
    override suspend fun readPassword(prompt: String): String {
        write.writeStringUtf8("pw $prompt\n")
        return read.readUTF8Line()!!
    }

    override suspend fun output(output: String) {
        write.writeStringUtf8("out $output\n")
    }

    override suspend fun done() {
        write.writeStringUtf8("done\n")
    }

    override suspend fun readInput(prompt: String): String {
        write.writeStringUtf8("in $prompt\n")
        return read.readUTF8Line()!!
    }

}