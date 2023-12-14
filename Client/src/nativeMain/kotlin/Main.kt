import com.kgit2.process.Command
import com.kgit2.process.Stdio
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.time.Duration.Companion.seconds

val kill = CoroutineScope(Job())

@OptIn(ExperimentalForeignApi::class)
val socketPath by lazy {
    val user = getenv("USER")!!.toKString()
    "/tmp/keePassXcFrontendSocket-$user"
}

fun main(args: Array<String>) {
    val terminal = isOnTerminal()

    kill.launch(Dispatchers.Unconfined) {
        delay(60.seconds)
        println("Forcing exit due to timeout")
        exit(1)
    }

    runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = try {
            aSocket(selectorManager).tcp().connect(UnixSocketAddress(socketPath))
        } catch (_: IllegalStateException) {
            println("Daemon is not running")
            exit(1)
            null
        }!!

        val receive = socket.openReadChannel()
        val write = socket.openWriteChannel(autoFlush = true)

        write.writeStringUtf8(args.joinToString(" ") + "\n")

        var done = false
        while (!done) {
            val line = receive.readUTF8Line()!!
            var cmdIndex = line.indexOf(" ")
            if (cmdIndex == -1) {
                cmdIndex = line.length
            }

            val cmd = line.substring(0, cmdIndex)
            val rest = if (cmdIndex < line.length) {
                line.substring(cmdIndex + 1)
            } else {
                ""
            }

            when (cmd) {
                "in" -> {
                    if (terminal) {
                        print(rest)
                        write.writeStringUtf8(readln() + "\n")
                    } else {
                        val child = Command("zenity").arg("--password").stdout(Stdio.Pipe).spawn()
                        val pw = child.getChildStdout()!!.readUTF8Line()
                        child.wait()
                        write.writeStringUtf8("$pw\n")
                    }
                }

                "out" -> {
                    println(rest)
                }

                "pw" -> {
                    if (terminal) {
                        print(rest)
                        write.writeStringUtf8(readPw() + "\n")
                    } else {
                        val child = Command("zenity").arg("--password").stdout(Stdio.Pipe).spawn()
                        val pw = child.getChildStdout()!!.readUTF8Line()
                        child.wait()
                        write.writeStringUtf8("$pw\n")
                    }
                }

                "done" -> {
                    done = true
                }
            }
        }

        socket.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
fun isOnTerminal(): Boolean {
    val res = isatty(STDIN_FILENO) == 1 && isatty(STDOUT_FILENO) == 1
    if (!res) {
        return false
    }

    val envVar = getenv("FORCE_GUI")?.toKString() ?: ""
    return envVar != "true"
}