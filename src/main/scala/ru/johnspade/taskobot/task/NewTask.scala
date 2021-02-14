package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.task.tags.{CreatedAt, Done, SenderName, TaskText}
import ru.johnspade.taskobot.user.tags.UserId

final case class NewTask(
  sender: UserId,
  text: TaskText,
  createdAt: CreatedAt,
  receiver: Option[UserId] = None,
  done: Done = Done(false),
  forwardFromId: Option[UserId] = None,
  forwardFromSenderName: Option[SenderName] = None
)
