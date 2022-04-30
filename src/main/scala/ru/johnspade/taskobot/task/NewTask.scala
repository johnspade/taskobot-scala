package ru.johnspade.taskobot.task

final case class NewTask(
    sender: Long,
    text: String,
    createdAt: Long,
    receiver: Option[Long] = None,
    done: Boolean = false,
    forwardFromId: Option[Long] = None,
    forwardFromSenderName: Option[String] = None
)
