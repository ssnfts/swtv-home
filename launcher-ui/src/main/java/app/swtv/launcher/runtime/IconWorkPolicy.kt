package app.swtv.launcher.runtime

/** Bounds accepted running/queued packages; obsolete completions cannot release newer work. */
class IconWorkPolicy(private val capacity: Int) {
    data class Ticket(val packageName: String, val generation: Int)
    private var generation = 0
    private val pending = mutableSetOf<Ticket>()

    @Synchronized fun beginGeneration(): Int {
        generation++
        pending.clear()
        return generation
    }

    @Synchronized fun reserve(packageName: String, token: Int): Ticket? {
        val ticket = Ticket(packageName, token)
        if (token != generation || pending.size >= capacity || ticket in pending) return null
        pending.add(ticket)
        return ticket
    }

    @Synchronized fun isCurrent(ticket: Ticket) = ticket.generation == generation
    @Synchronized fun pendingCount() = pending.size
    @Synchronized fun finish(ticket: Ticket) { pending.remove(ticket) }
}
