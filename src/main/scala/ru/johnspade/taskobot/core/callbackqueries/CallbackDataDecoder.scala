package ru.johnspade.taskobot.core.callbackqueries

trait CallbackDataDecoder[F[_], T] {
  def decode(queryData: String): DecodeResult[F, T]
}
