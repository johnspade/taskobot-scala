package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.task.tags._
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags._

case class BotTask(
  id: TaskId,
  sender: UserId,
  text: TaskText,
  receiver: Option[UserId],
  createdAt: CreatedAt,
  doneAt: Option[DoneAt] = None,
  done: Done = Done(false),
  forwardFromId: Option[UserId] = None,
  forwardFromSenderName: Option[SenderName] = None
)

case class TaskWithCollaborator(
  id: TaskId,
  text: TaskText,
  collaborator: Option[User]
)
