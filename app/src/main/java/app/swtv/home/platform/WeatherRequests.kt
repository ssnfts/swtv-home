package app.swtv.home.platform

/** Owns active request identity and the latest foreground status subscriber. */
class WeatherRequests {
    data class Ticket(val city: String, val generation: Int)
    @Volatile private var generation = 0
    private var active: Ticket? = null
    private var callback: ((String) -> Unit)? = null
    val activeCity: String? get() = active?.city

    fun observe(city: String, done: (String) -> Unit): Boolean {
        if (active?.city != city) return false
        callback = done
        return true
    }

    fun begin(city: String, done: (String) -> Unit): Ticket {
        val ticket = Ticket(city, ++generation)
        active = ticket
        callback = done
        return ticket
    }

    fun isCurrent(ticket: Ticket) = ticket.generation == generation

    fun complete(ticket: Ticket, value: String): Boolean {
        if (!isCurrent(ticket) || active != ticket) return false
        val target = callback
        active = null
        callback = null
        target?.invoke(value)
        return true
    }

    fun cancel() {
        generation++
        active = null
        callback = null
    }
}
