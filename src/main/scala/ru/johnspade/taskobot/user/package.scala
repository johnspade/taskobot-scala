package ru.johnspade.taskobot

import supertagged.TaggedType

package object user {
  object tags {
    object UserId extends TaggedType[Long]
    type UserId = UserId.Type

    object FirstName extends TaggedType[String]
    type FirstName = FirstName.Type

    object LastName extends TaggedType[String]
    type LastName = LastName.Type

    object ChatId extends TaggedType[Long]
    type ChatId = ChatId.Type
  }
}
