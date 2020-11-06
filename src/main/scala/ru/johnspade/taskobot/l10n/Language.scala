package ru.johnspade.taskobot.l10n

import enumeratum.EnumEntry.Uppercase
import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class Language(val languageTag: String, val languageName: String) extends EnumEntry with Uppercase

object Language extends Enum[Language] {
  case object English extends Language("en", "English")
  case object Russian extends Language("ru", "Русский")
  case object Turkish extends Language("tr", "Turkish")
  case object Italian extends Language("it", "Italian")

  override val values: IndexedSeq[Language] = findValues
}
