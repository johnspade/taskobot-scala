package ru.johnspade.taskobot.core

import cats.Id
import zio.test._
import zio.test.Assertion._

object PageSpec extends ZIOSpecDefault:
  private val items = List(1, 2, 3, 4, 5)
  private val makeRequest: (Long, Int) => Id[List[Int]] =
    (offset, pageSize) => items.slice(offset.toInt, offset.toInt + pageSize)

  override def spec: ZSpec[TestEnvironment, Nothing] = suite("PageSpec")(
    test("single page") {
      val page = Page.request(number = 0, size = 5, f = makeRequest)
      assertTrue(page == Page(List(1, 2, 3, 4, 5), 0, hasPrevious = false, hasNext = false))
    },
    test("first page") {
      val page = Page.request(number = 0, size = 3, f = makeRequest)
      assertTrue(page == Page(List(1, 2, 3), 0, hasPrevious = false, hasNext = true))
    },
    test("page N") {
      val page = Page.request(number = 1, size = 2, f = makeRequest)
      assertTrue(page == Page(List(3, 4), 1, hasPrevious = true, hasNext = true))
    },
    test("last page") {
      val page = Page.request(number = 2, size = 2, f = makeRequest)
      assertTrue(page == Page(List(5), 2, hasPrevious = true, hasNext = false))
    }
  )
