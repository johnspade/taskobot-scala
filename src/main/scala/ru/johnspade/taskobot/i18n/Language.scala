package ru.johnspade.taskobot.i18n

import enumeratum.values.{StringEnum, StringEnumEntry}

import scala.collection.immutable.IndexedSeq

sealed abstract class Language(val value: String, val languageName: String) extends StringEnumEntry

object Language extends StringEnum[Language] {
  case object English extends Language("en", "English")
  case object Russian extends Language("ru", "Русский")
  case object Turkish extends Language("tr", "Turkish")
  case object Italian extends Language("it", "Italian")
  case object TraditionalChinese extends Language("zh_TW", "Traditional Chinese")

  override val values: IndexedSeq[Language] = findValues
}
