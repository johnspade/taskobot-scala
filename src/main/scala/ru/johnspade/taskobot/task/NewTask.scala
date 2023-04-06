package ru.johnspade.taskobot.task

import java.time.Instant
import java.time.ZoneId

final case class NewTask(
    sender: Long,
    text: String,
    createdAt: Instant,
    timezone: ZoneId,
    receiver: Option[Long] = None,
    done: Boolean = false,
    forwardFromId: Option[Long] = None,
    forwardFromSenderName: Option[String] = None
)
