package ru.johnspade.taskobot

import scala.annotation.StaticAnnotation

package object csv {
  final case class TypeId(id: Int) extends StaticAnnotation
}
