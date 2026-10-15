package dev.twiview.data

import java.util.UUID

class ChatBuffer(private val max: Int) {
    private val seen = HashSet<String>()

    class Result(
        val messages: List<ChatMessage>,
        val changed: Boolean,
        val connection: ChatEvent.Connection?,
        val userState: ChatEvent.UserState?,
    )

    fun apply(current: List<ChatMessage>, events: List<ChatEvent>, now: () -> Long = System::currentTimeMillis): Result {
        val list = ArrayList(current)
        var changed = false
        var connection: ChatEvent.Connection? = null
        var userState: ChatEvent.UserState? = null
        for (e in events) {
            when (e) {
                is ChatEvent.Message -> if (seen.add(e.message.id)) {
                    list += e.message
                    changed = true
                }
                is ChatEvent.Delete -> {
                    val i = list.indexOfLast { it.id == e.messageId }
                    if (i >= 0 && !list[i].deleted) {
                        list[i] = list[i].copy(deleted = true)
                        changed = true
                    }
                }
                is ChatEvent.ClearUser -> {
                    for (i in list.indices) if (list[i].login == e.login && !list[i].deleted) {
                        list[i] = list[i].copy(deleted = true)
                        changed = true
                    }
                }
                ChatEvent.ClearAll -> Unit
                is ChatEvent.Connection -> connection = e
                is ChatEvent.UserState -> userState = e
                is ChatEvent.Notice -> {
                    list += ChatMessage(
                        id = "notice-" + UUID.randomUUID(), userId = "", login = "", displayName = "",
                        color = null, text = "", emotes = emptyList(), badges = emptyList(),
                        timestamp = now(), systemText = e.text,
                    )
                    changed = true
                }
            }
        }
        if (list.size > max) {
            val drop = list.subList(0, list.size - max)
            drop.forEach { seen.remove(it.id) }
            drop.clear()
        }
        return Result(list, changed, connection, userState)
    }
}
