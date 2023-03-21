package ru.johnspade.taskobot.task

import java.time.Instant
import java.time.ZoneId

final case class NewTask(
    sender: Long,
    text: String,
    createdAt: Instant,
    receiver: Option[Long] = None,
    done: Boolean = false,
    timezone: ZoneId,
    forwardFromId: Option[Long] = None,
    forwardFromSenderName: Option[String] = None
)
