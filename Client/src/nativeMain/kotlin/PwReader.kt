import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
fun readPw(): String {
    lateinit var res: String
    memScoped {
        val oldTerm = this.alloc<termios>()
        var newTerm = this.alloc<termios>()

        tcgetattr(STDIN_FILENO, oldTerm.ptr)
        tcgetattr(STDIN_FILENO, newTerm.ptr)

        newTerm.c_lflag= newTerm.c_lflag.and(ECHO.inv().toUInt())

        tcsetattr(STDIN_FILENO, TCSANOW, newTerm.ptr)

        res = readln()

        tcsetattr(STDIN_FILENO, TCSANOW, oldTerm.ptr)
    }

    println()

    return res
}