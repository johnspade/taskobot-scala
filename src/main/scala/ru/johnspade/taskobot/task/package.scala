package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Tagged

package object task {
  object tags {
    object TaskId extends Tagged[Long]
    type TaskId = TaskId.Type

    object TaskText extends Tagged[String]
    type TaskText = TaskText.Type

    object CreatedAt extends Tagged[Long]
    type CreatedAt = CreatedAt.Type

    object DoneAt extends Tagged[Long]
    type DoneAt = DoneAt.Type

    object Done extends Tagged[Boolean]
    type Done = Done.Type

    object SenderName extends Tagged[String]
    type SenderName = SenderName.Type
  }
}
