package ru.johnspade.taskobot

import ru.makkarpov.scalingua.Messages

package object i18n {
  implicit val messages: Messages = Messages.compiled()
}
