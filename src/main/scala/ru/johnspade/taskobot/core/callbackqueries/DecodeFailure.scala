package ru.johnspade.taskobot.core.callbackqueries

abstract class DecodeFailure(message: String) extends RuntimeException with Product with Serializable {
  final override def getMessage: String = message
}

final case class ParseError(msg: String) extends DecodeFailure(msg)

final case class DecodeError(msg: String) extends DecodeFailure(msg)
