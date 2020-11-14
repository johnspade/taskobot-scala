package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.task.tags.{CreatedAt, Done, TaskText}
import ru.johnspade.taskobot.user.tags.UserId

final case class NewTask(
  sender: UserId,
  text: TaskText,
  createdAt: CreatedAt,
  receiver: Option[UserId] = None,
  done: Done = Done(false)
)
