package ru.johnspade.taskobot.core.callbackqueries

import cats.data.OptionT
import cats.{Monad, MonadError}
import telegramium.bots.CallbackQuery
import telegramium.bots.client.Method

object CallbackQueryHandler {
  def handle[F[_]: Monad, I](
    cb: CallbackQuery,
    routes: CallbackQueryRoutes[I, F],
    decoder: CallbackDataDecoder[F, I],
    onNotFound: CallbackQuery => F[Option[Method[_]]]
  )(implicit F: MonadError[F, Throwable]): F[Option[Method[_]]] =
    (for {
      queryData <- OptionT.fromOption[F](cb.data)
      data <- OptionT.liftF(F.rethrow(decoder.decode(queryData).value))
      res <- routes.run(CallbackQueryData(data, cb))
    } yield res)
      .getOrElseF(onNotFound(cb))
}
