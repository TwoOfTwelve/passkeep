import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*

object PwCache {
    private val timeout = 30L * 1000
    private var enteredTime = 0L
    private var cachedPw: String? = null
    private val scope = CoroutineScope(Job())

    init {
        mlockall(MCL_FUTURE)
        scope.launch(Dispatchers.IO) {
            while(this.isActive) {
                var diff = timeout - (getTimeMillis() - enteredTime)
                if (diff < 0) {
                    diff = timeout
                }

                delay(diff)

                if(getTimeMillis() - enteredTime >= timeout) {
                    cachedPw = null
                }
            }
        }
    }

    suspend fun getPassword(io: IOHandler): String {
        enteredTime = getTimeMillis()
        var tmpPw: String? = cachedPw
        while(tmpPw == null) {
            cachedPw = io.readPassword("Database password: ")
            enteredTime = getTimeMillis()
            tmpPw = cachedPw
        }

        return tmpPw
    }

    fun stop() {
        scope.cancel()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getTimeMillis(): Long {
        return memScoped {
            val spec = this.alloc<timespec>()
            clock_gettime(1, spec.ptr)
            (spec.tv_sec * 1000) + (spec.tv_nsec / 1000000)
        }
    }
}