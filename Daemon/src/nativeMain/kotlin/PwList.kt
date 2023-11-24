object PwList {
    private var passwords = listOf<String>()
    private var initialized = false

    suspend fun list(io: IOHandler): List<String> {
        if(!initialized) {
            passwords = CliHandler.fetchList(io)
            initialized = true
        }

        return this.passwords
    }
}