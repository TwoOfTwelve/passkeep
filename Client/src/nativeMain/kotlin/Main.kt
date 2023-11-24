import com.kgit2.process.Command
import com.kgit2.process.Stdio
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import platform.posix.STDIN_FILENO
import platform.posix.exit
import platform.posix.getenv
import platform.posix.isatty

@OptIn(ExperimentalForeignApi::class)
val socketPath by lazy {
    val user = getenv("USER")!!.toKString()
    "/tmp/keePassXcFrontendSocket-$user"
}

fun main(args: Array<String>) {
    val terminal = isatty(STDIN_FILENO) == 1

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
        while(!done) {
            val line = receive.readUTF8Line()!!
            var cmdIndex = line.indexOf(" ")
            if(cmdIndex == -1) {
                cmdIndex = line.length
            }

            val cmd = line.substring(0, cmdIndex)
            val rest = if(cmdIndex < line.length) {
                line.substring(cmdIndex + 1)
            } else {
                ""
            }

            when(cmd) {
                "in" -> {
                    if(terminal) {
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
                    if(terminal) {
                        print(rest)
                        write.writeStringUtf8(readPw() + "\n")
                    } else {
                        val child = Command("zenity").arg("--password").stdout(Stdio.Pipe).spawn()
                        val pw = child.getChildStdout()!!.readUTF8Line()
                        child.wait()
                        write.writeStringUtf8("$pw\n")
                    }
                }
                "done" -> {done = true}
            }
        }

        socket.close()
    }
}
