package ru.johnspade.taskobot.i18n

import enumeratum.EnumEntry.Uppercase
import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class Language(val languageTag: String, val languageName: String) extends EnumEntry with Uppercase

object Language extends Enum[Language] {
  case object English extends Language("en-US", "English")
  case object Russian extends Language("ru-RU", "Русский")
  case object Turkish extends Language("tr-TR", "Turkish")
  case object Italian extends Language("it-IT", "Italian")

  override val values: IndexedSeq[Language] = findValues
}
