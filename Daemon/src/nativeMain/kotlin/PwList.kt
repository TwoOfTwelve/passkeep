object PwList {
    private var passwords = listOf<String>()
    private var initialized = false

    suspend fun list(io: IOHandler): List<String> {
        if(!initialized) {
            var remainingTries = 3
            while(remainingTries-- > 0 && !initialized) {
                passwords = CliHandler.fetchList(io)
                if (passwords.isNotEmpty()) {
                    initialized = true
                } else {
                    if(remainingTries > 0) {
                        io.output("No passwords received. Try again.")
                    } else {
                        io.output("No passwords found.")
                    }
                }
            }
        }

        return this.passwords
    }

    fun reset() {
        passwords = mutableListOf()
        initialized = false
    }
}