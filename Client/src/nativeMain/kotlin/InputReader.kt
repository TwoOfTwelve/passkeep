import com.kgit2.process.Command
import com.kgit2.process.Stdio
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
private val runsOnTerminal by lazy {
    val isATty = isatty(STDIN_FILENO) == 1 && isatty(STDOUT_FILENO) == 1
    if (!isATty) {
        false
    } else {
        val forceGui = getenv("FORCE_GUI")?.toKString() ?: ""
        forceGui != "true"
    }
}

/**
 * Reads a password from the terminal or from a gui window, depending on the environment
 */
fun readPw(prompt: String): String {
    return if (runsOnTerminal) {
        print(prompt)
        readPwFromTerminal()
    } else {
        readFromZenity(ZenityType.PASSWORD, prompt)
    }
}

fun readText(prompt: String): String {
    return if(runsOnTerminal) {
        print(prompt)
        readln()
    } else {
        readFromZenity(ZenityType.ENTRY, prompt)
    }
}

private fun readFromZenity(zenityType: ZenityType, prompt: String): String {
    val child = Command("zenity").args("--${zenityType.name.lowercase()}", "--text", prompt).stdout(Stdio.Pipe).spawn()
    val pw = child.getChildStdout()!!.readUTF8Line() ?: ""
    child.wait()
    return pw
}

@OptIn(ExperimentalForeignApi::class)
private fun readPwFromTerminal(): String {
    lateinit var res: String
    memScoped {
        val oldTerm = this.alloc<termios>()
        val newTerm = this.alloc<termios>()

        tcgetattr(STDIN_FILENO, oldTerm.ptr)
        tcgetattr(STDIN_FILENO, newTerm.ptr)

        newTerm.c_lflag = newTerm.c_lflag.and(ECHO.inv().toUInt())

        tcsetattr(STDIN_FILENO, TCSANOW, newTerm.ptr)

        res = readln()

        tcsetattr(STDIN_FILENO, TCSANOW, oldTerm.ptr)
    }

    println()

    return res
}

enum class ZenityType {
    PASSWORD, ENTRY
}