package ru.johnspade.taskobot

import telegramium.bots.client.{Method, MethodReq}
import zio.test.Assertion
import zio.test.AssertionM.Render.param

object TestAssertions {
  def isMethodsEqual[Res](reference: Method[Res]): Assertion[Method[_]] =
    Assertion.assertion("isMethodsEqual")(param(reference)) { actual =>
      compareMethods(reference, actual.asInstanceOf[Method[Res]])
    }

  private def compareMethods[Res](expected: Method[Res], actual: Method[Res]): Boolean = {
    val actualReq = actual.asInstanceOf[MethodReq[Res]]
    val expectedReq = expected.asInstanceOf[MethodReq[Res]]
    actualReq.name == expectedReq.name && actualReq.json == expectedReq.json && actualReq.files == expectedReq.files
  }
}
