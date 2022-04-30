package ru.johnspade.taskobot.messages

import doobie.Meta

enum Language(val value: String, val languageName: String):
  case English            extends Language("en", "English")
  case Russian            extends Language("ru", "Русский")
  case Turkish            extends Language("tr", "Turkish")
  case Italian            extends Language("it", "Italian")
  case TraditionalChinese extends Language("zh_TW", "Traditional Chinese")
  case Spanish            extends Language("es_ES", "Spanish")

object Language:
  def withValue(value: String): Option[Language] =
    Language.values.find(_.value == value)

  given Meta[Language] = Meta[String].timap(v =>
    Language.withValue(v).getOrElse(throw new RuntimeException(s"Unknown language: $v"))
  )(_.value)
