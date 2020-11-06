package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.task.tags._
import ru.johnspade.taskobot.user.tags._

case class BotTask(
  id: TaskId,
  sender: UserId,
  text: TaskText,
  receiver: Option[UserId],
  createdAt: CreatedAt,
  doneAt: Option[DoneAt],
  done: Boolean
)
