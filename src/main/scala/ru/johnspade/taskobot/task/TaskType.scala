package ru.johnspade.taskobot.task

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class TaskType extends EnumEntry

object TaskType extends Enum[TaskType] {
  case object Shared extends TaskType
  case object Personal extends TaskType

  override val values: IndexedSeq[TaskType] = findValues
}
