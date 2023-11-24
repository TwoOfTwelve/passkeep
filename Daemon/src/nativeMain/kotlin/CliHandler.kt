import com.kgit2.process.Command
import com.kgit2.process.Stdio

object CliHandler {
    private val dbFile = "/home/alexander/Passwords.kdbx"

    suspend fun fetchList(io: IOHandler): List<String> {
        val res = mutableListOf<String>()
        runCommand(io, "ls", dbFile) {
            res.add(it)
        }
        return res
    }

    suspend fun copyPassword(pwName: String, io: IOHandler) {
        io.output("copying password")
        runCommand(io, "clip", dbFile, pwName)
        io.output("clipboard cleared")
    }

    suspend fun copyUsername(pwName: String, io: IOHandler) {
        io.output("copying username")
        runCommand(io, "clip", "-a", "username", dbFile, pwName, "0")
    }

    suspend fun copyUrl(pwName: String, io: IOHandler) {
        io.output("copying username")
        runCommand(io, "clip", "-a", "url", dbFile, pwName, "0")
    }

    private suspend fun runCommand(
        io: IOHandler, vararg args: String, lineHandler: (suspend (String) -> Unit)? = null
    ) {
        println("running command: $args")
        val child = Command("keepassxc-cli").args(*args).arg("-q")
            .stdin(Stdio.Pipe)
            .stdout(
                if (lineHandler != null) {
                    Stdio.Pipe
                } else {
                    Stdio.Null
                }
            ).stderr(Stdio.Null)
            .spawn()

        val stdin = child.getChildStdin()!!
        stdin.append(PwCache.getPassword(io)).append("\n")
        stdin.flush()

        if (lineHandler != null) {
            val stdout = child.getChildStdout()!!
            stdout.lines().forEach {
                lineHandler(it)
            }
        }

        child.wait()
    }
}