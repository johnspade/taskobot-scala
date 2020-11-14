package ru.johnspade.taskobot.core

import cats.{Applicative, Defer, Monad}
import cats.data.{EitherT, Kleisli, OptionT}
import telegramium.bots.client.Method
import cats.implicits._
import telegramium.bots.CallbackQuery

package object callbackqueries {
  type DecodeResult[F[_], I] = EitherT[F, DecodeFailure, I]

  type CallbackQueries[I, F[_]] = Kleisli[F, CallbackQueryData[I], Option[Method[_]]]

  type CallbackQueryRoutes[I, F[_]] = CallbackQueries[I, OptionT[F, *]]

  type CallbackQueryContextRoutes[I, A, F[_]] = Kleisli[OptionT[F, *], ContextCallbackQuery[I, A], Option[Method[_]]]

  type Middleware[F[_], A, B, C, D] = Kleisli[F, A, B] => Kleisli[F, C, D]

  type CallbackQueryContextMiddleware[I, A, F[_]] = Middleware[
    OptionT[F, *],
    ContextCallbackQuery[I, A], Option[Method[_]],
    CallbackQueryData[I], Option[Method[_]]
  ]

  object CallbackQueryRoutes {
    def of[I, F[_]: Defer: Applicative](pf: PartialFunction[CallbackQueryData[I], F[Option[Method[_]]]]): CallbackQueryRoutes[I, F] =
      Kleisli(input => OptionT(Defer[F].defer(pf.lift(input).sequence)))
  }

  object CallbackQueryContextRoutes {
    def of[I, A, F[_]: Defer: Applicative](pf: PartialFunction[ContextCallbackQuery[I, A], F[Option[Method[_]]]]): CallbackQueryContextRoutes[I, A, F] =
      Kleisli(cb => OptionT(Defer[F].defer(pf.lift(cb).sequence)))
  }

  object CallbackQueryContextMiddleware {
    def apply[F[_]: Monad, I, A](
      retrieveContext: Kleisli[OptionT[F, *], CallbackQuery, A]
    ): CallbackQueryContextMiddleware[I, A, F] =
      _.compose(Kleisli((cb: CallbackQueryData[I]) => retrieveContext(cb.cb).map(ContextCallbackQuery(_, cb))))
  }
}
