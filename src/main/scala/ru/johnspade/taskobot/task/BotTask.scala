package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.user.User

case class BotTask(
    id: Long,
    sender: Long,
    text: String,
    receiver: Option[Long],
    createdAt: Long,
    doneAt: Option[Long] = None,
    done: Boolean = false,
    forwardFromId: Option[Long] = None,
    forwardFromSenderName: Option[String] = None
)

case class TaskWithCollaborator(
    id: Long,
    text: String,
    collaborator: Option[User]
)
