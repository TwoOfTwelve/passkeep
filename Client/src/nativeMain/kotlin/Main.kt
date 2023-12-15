import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.*
import platform.posix.exit
import platform.posix.getenv
import kotlin.time.Duration.Companion.seconds

val kill = CoroutineScope(Job())

fun main(args: Array<String>) {
    startSelfTimeout()

    runBlocking {
        handleConnection(args)
    }
}

/**
 * Exits this process after a timeout has passed.
 */
private fun startSelfTimeout() {
    kill.launch(Dispatchers.Unconfined) {
        delay(60.seconds)
        println("Forcing exit due to timeout")
        exit(1)
    }
}