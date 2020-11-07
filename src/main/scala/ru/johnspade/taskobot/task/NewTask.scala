package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.task.tags.TaskText
import ru.johnspade.taskobot.user.tags.UserId

final case class NewTask(
  sender: UserId,
  text: TaskText
)
