package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Tagged

package object user {
  object tags {
    object UserId extends Tagged[Long]
    type UserId = UserId.Type

    object FirstName extends Tagged[String]
    type FirstName = FirstName.Type

    object LastName extends Tagged[String]
    type LastName = LastName.Type

    object ChatId extends Tagged[Long]
    type ChatId = ChatId.Type
  }
}
