package ru.johnspade.taskobot.core

import doobie._
import supertagged.TaggedType

trait TaggedMeta[T] extends TaggedType[T] {
  implicit def taggedMeta(implicit meta: Meta[T]): Meta[Type] = apply(meta)
}
