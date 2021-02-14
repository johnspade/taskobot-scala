package ru.johnspade.taskobot

import supertagged.TaggedType

package object task {
  object tags {
    object TaskId extends TaggedType[Long]
    type TaskId = TaskId.Type

    object TaskText extends TaggedType[String]
    type TaskText = TaskText.Type

    object CreatedAt extends TaggedType[Long]
    type CreatedAt = CreatedAt.Type

    object DoneAt extends TaggedType[Long]
    type DoneAt = DoneAt.Type

    object Done extends TaggedType[Boolean]
    type Done = Done.Type

    object SenderName extends TaggedType[String]
    type SenderName = SenderName.Type
  }
}
