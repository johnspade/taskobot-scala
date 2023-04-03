package ru.johnspade.taskobot

import cats.syntax.option.*
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.task.NewTask
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.task.TaskRepositoryLive
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.UserRepositoryLive
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import zio.*
import zio.test.TestAspect.sequential
import zio.test.*

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object BotServiceSpec extends ZIOSpecDefault:
  private val testEnv = ZLayer.make[BotService with UserRepository with TaskRepository](
    TestDatabase.layer,
    UserRepositoryLive.layer,
    TaskRepositoryLive.layer,
    MsgConfig.live,
    MessageServiceLive.layer,
    BotServiceLive.layer
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("BotServiceSpec")(
    (suite("updateUser")(
      test("should convert and create a Telegram user") {
        val tgUser = telegramium.bots.User(1337, isBot = false, "John")
        val expectedUser =
          User(
            1337L,
            "John",
            Language.English,
            chatId = Some(123L),
            timezone = Some(madridTimezone),
            blockedBot = Some(false)
          )
        for
          user      <- BotService.updateUser(tgUser, chatId = Some(123L), timezone = Some(madridTimezone))
          savedUser <- UserRepository.findById(1337L)
        yield assertTrue(user == expectedUser) && assertTrue(savedUser.contains(expectedUser))
      },
      test("should convert and update a Telegram user") {
        val tgUser = telegramium.bots.User(1337, isBot = false, "John", "Spade".some)
        val expectedUser = User(
          1337L,
          "John",
          Language.English,
          lastName = "Spade".some,
          chatId = Some(123L),
          timezone = Some(madridTimezone),
          blockedBot = Some(false)
        )
        for
          _         <- UserRepository.createOrUpdate(User(1337L, "John", Language.English))
          user      <- BotService.updateUser(tgUser, chatId = Some(123L), timezone = Some(madridTimezone))
          savedUser <- UserRepository.findById(1337L)
        yield assertTrue(user == expectedUser) && assertTrue(savedUser.contains(expectedUser))
      }
    ) @@ sequential)
      .provideCustomShared(testEnv),
    suite("getTasks")(
      test("should return tasks") {
        val expectedMessageEntities = List(
          plain"Chat: ",
          bold"Alice",
          lineBreak,
          plain"1. task2",
          italic" – Bob",
          lineBreak,
          plain"2. task1",
          italic" – Bob",
          lineBreak
        )
        for
          now <- Clock.instant
          task1 = NewTask(322L, "task1", now, alice.id.some, timezone = UTC)
          task2 = NewTask(322L, "task2", now.plusSeconds(1), alice.id.some, timezone = UTC)
          expectedTasks = List(
            BotTask(2L, task2.sender, task2.text, task2.receiver, task2.createdAt, timezone = Some(UTC)),
            BotTask(1L, task1.sender, task1.text, task1.receiver, task1.createdAt, timezone = Some(UTC))
          )
          _                      <- UserRepository.createOrUpdate(bob)
          _                      <- UserRepository.createOrUpdate(alice)
          _                      <- TaskRepository.create(task1)
          _                      <- TaskRepository.create(task2)
          pageAndMessageEntities <- BotService.getTasks(bob, alice, 0)
        yield assertTrue(pageAndMessageEntities._1.items == expectedTasks) &&
          assertTrue(pageAndMessageEntities._2 == expectedMessageEntities) &&
          assertTrue(pageAndMessageEntities._1.number == 0)
      }
        .provideCustomShared(testEnv),
      test("should cut long tasks") {
        for
          now <- Clock.instant
          tasks = List.tabulate(5) { _ =>
            NewTask(
              sender = bob.id,
              text = longTaskText,
              createdAt = now,
              receiver = alice.id.some,
              timezone = UTC
            )
          }
          _      <- UserRepository.createOrUpdate(bob)
          _      <- UserRepository.createOrUpdate(alice)
          _      <- ZIO.foreachDiscard(tasks)(TaskRepository.create)
          result <- BotService.getTasks(bob, alice, 0)
          entitiesAssertions = assertTrue(
            result._2 == List(
              plain"Chat: ",
              bold"Alice",
              lineBreak,
              plain"1. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultric...",
              italic" – Bob",
              lineBreak,
              plain"2. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultric...",
              italic" – Bob",
              lineBreak,
              plain"3. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultric...",
              italic" – Bob",
              lineBreak,
              plain"4. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultric...",
              italic" – Bob",
              lineBreak,
              plain"5. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultric...",
              italic" – Bob",
              lineBreak
            )
          )
          lengthAssertions = assertTrue(result._2.map(_.text).iterator.mkString("").length <= MessageLimit)
        yield entitiesAssertions && lengthAssertions
      }.provideCustomShared(testEnv),
      test("should not cut long tasks if message limits are not exceeded") {
        for
          now <- Clock.instant
          tasks = NewTask(bob.id, longTaskText, now, alice.id.some, timezone = UTC) ::
            List.tabulate(4) { _ =>
              NewTask(bob.id, "Fix bugs", now, alice.id.some, timezone = UTC)
            }
          _      <- UserRepository.createOrUpdate(bob)
          _      <- UserRepository.createOrUpdate(alice)
          _      <- ZIO.foreachDiscard(tasks)(TaskRepository.create)
          result <- BotService.getTasks(bob, alice, 0)
          assertions = assertTrue(
            result._2 == List(
              plain"Chat: ",
              bold"Alice",
              lineBreak,
              plain"1. $longTaskText",
              italic" – Bob",
              lineBreak,
              plain"2. Fix bugs",
              italic" – Bob",
              lineBreak,
              plain"3. Fix bugs",
              italic" – Bob",
              lineBreak,
              plain"4. Fix bugs",
              italic" – Bob",
              lineBreak,
              plain"5. Fix bugs",
              italic" – Bob",
              lineBreak
            )
          )
        yield assertions
      }
        .provideCustomShared(testEnv)
    ),
    suite("createTaskDetails")(
      test("should return task details") {
        for
          _   <- TestClock.setTime(Instant.EPOCH)
          now <- Clock.instant
          deadline = LocalDateTime.ofInstant(now, UTC)
          task = BotTask(
            id = 0L,
            sender = 0L,
            text = "Full text",
            receiver = 0L.some,
            createdAt = now,
            deadline = deadline.some,
            timezone = UTC.some
          )
          details <- BotService.createTaskDetails(task, Language.English)
        yield assertTrue(
          details == List(
            plain"",
            plain"Full text",
            lineBreak,
            lineBreak,
            bold"🕒 Due date: 1970-01-01 00:00",
            lineBreak,
            lineBreak,
            italic"Created at: 1970-01-01 00:00"
          )
        )
      }
        .provideCustomShared(testEnv),
      test("should cut long texts") {
        for
          _   <- TestClock.setTime(Instant.EPOCH)
          now <- Clock.instant
          deadline = LocalDateTime.ofInstant(now, UTC)
          task = BotTask(
            id = 0L,
            sender = 0L,
            text = veryLongTaskText,
            receiver = 0L.some,
            createdAt = now,
            deadline = deadline.some,
            timezone = UTC.some
          )
          details <- BotService.createTaskDetails(task, Language.English)
        yield assertTrue(
          details == List(
            plain"",
            Plain(shortenedVeryLongTaskText),
            lineBreak,
            lineBreak,
            bold"🕒 Due date: 1970-01-01 00:00",
            lineBreak,
            lineBreak,
            italic"Created at: 1970-01-01 00:00"
          )
        ) && assertTrue(details.map(_.text).mkString("").length <= MessageLimit)
      }.provideCustomShared(testEnv),
      test("should not cut long tasks if message limits are not exceeded") {
        for
          _   <- TestClock.setTime(Instant.EPOCH)
          now <- Clock.instant
          deadline = LocalDateTime.ofInstant(now, UTC)
          task = BotTask(
            id = 0L,
            sender = 0L,
            text = longTaskText,
            receiver = 0L.some,
            createdAt = now,
            deadline = deadline.some,
            timezone = UTC.some
          )
          details <- BotService.createTaskDetails(task, Language.English)
        yield assertTrue(
          details == List(
            plain"",
            Plain(longTaskText),
            lineBreak,
            lineBreak,
            bold"🕒 Due date: 1970-01-01 00:00",
            lineBreak,
            lineBreak,
            italic"Created at: 1970-01-01 00:00"
          )
        )
      }.provideCustomShared(testEnv)
    )
  )

  private val bob   = User(322L, "Bob", Language.English)
  private val alice = User(911L, "Alice", Language.English)
  private val longTaskText =
    "Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultrices malesuada arcu."
  private val veryLongTaskText =
    "Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultrices malesuada arcu. Praesent consequat, sapien a mattis venenatis, erat odio dapibus velit, vel interdum velit risus id nisl. Nunc vel aliquet nulla, in malesuada quam. Etiam iaculis massa ac ante ullamcorper consectetur. Pellentesque nec malesuada tellus. Sed venenatis dignissim dolor, sit amet viverra magna fringilla non. Suspendisse interdum ultricies justo, ac consectetur augue vestibulum in. Nam eget sapien nec neque pharetra dignissim. Praesent eget risus ut lacus suscipit dignissim. Sed in massa vel nulla auctor hendrerit vel sit amet nunc. Maecenas auctor justo nec nunc sollicitudin, vitae hendrerit sapien lobortis. Donec placerat dolor ac elit lobortis, eget suscipit quam rutrum. Nulla at lacinia ante. In hac habitasse platea dictumst. Suspendisse vitae lectus vel mi pulvinar ultrices. Nam quis rutrum massa. Suspendisse ut mauris mi. Nulla laoreet vestibulum lacus, in luctus mauris malesuada in. Praesent ut odio ac felis vulputate pretium vel eu nibh. Proin auctor aliquam justo, sit amet ultricies nisl interdum vel. Maecenas et lobortis augue. Nulla facilisi. Sed maximus, arcu in feugiat lobortis, velit augue bibendum lectus, vel pharetra lectus nibh eu justo. Suspendisse non quam non diam faucibus efficitur eu vel leo. Sed eleifend metus non augue scelerisque bibendum. Nullam fringilla dolor non lorem suscipit interdum. Aenean euismod vel ante id feugiat. Donec pretium erat vel nulla aliquam, vitae feugiat odio vulputate. Morbi nec mauris sed enim blandit euismod. Fusce a lectus vitae risus malesuada facilisis id id mi. Fusce venenatis est nec purus vulputate, vel ultricies neque vulputate. Integer at sapien eget augue bibendum rutrum. Integer vel risus eget massa aliquam malesuada ac sed lectus. Donec euismod velit vel leo convallis, id volutpat nisl finibus. Vivamus tempor risus euismod, non faucibus augue dictum. Maecenas at velit non arcu consequat efficitur sit amet eu odio. Donec vel lorem eu neque lobortis vulputate vel vel eros. In eleifend libero eget eros elementum efficitur. Nulla facilisi. Fusce bibendum eget mi eu mattis. Sed luctus, odio ut gravida commodo, leo neque sagittis purus, nec bibendum est justo eget felis. Sed quis enim vel sapien mollis vulputate. Quisque euismod ex eget metus efficitur, non bibendum elit commodo. Integer non elit auctor, cursus ipsum ut, consectetur purus. Duis dapibus lacinia nibh, nec fermentum est consequat vitae. Aenean dignissim, augue sed gravida sollicitudin, sapien justo commodo risus, vitae tristique est tortor ut lectus. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Integer dapibus vestibulum lacus eu euismod. Praesent aliquet dapibus lacus, a dictum nulla faucibus ac. Proin ac lacus nunc. Sed at justo a ex sagittis rutrum in ac augue. Suspendisse potenti. Nulla facilisi. Nam vitae augue sapien. Aliquam erat volutpat. Vestibulum ullamcorper varius mauris, vel convallis est commodo sed. Sed interdum arcu at enim varius, id viverra ante mattis. Suspendisse eu nibh ligula. Morbi venenatis vehicula velit eu dictum. Nam at orci convallis, mollis dolor sed, eleifend orci. Sed sed bibendum velit. In tincidunt libero vel eros tincidunt, a pellentesque mauris venenatis. Nam quis mauris vel enim elementum semper. Sed tristique lacus ."
  private val shortenedVeryLongTaskText =
    "Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultrices malesuada arcu. Praesent consequat, sapien a mattis venenatis, erat odio dapibus velit, vel interdum velit risus id nisl. Nunc vel aliquet nulla, in malesuada quam. Etiam iaculis massa ac ante ullamcorper consectetur. Pellentesque nec malesuada tellus. Sed venenatis dignissim dolor, sit amet viverra magna fringilla non. Suspendisse interdum ultricies justo, ac consectetur augue vestibulum in. Nam eget sapien nec neque pharetra dignissim. Praesent eget risus ut lacus suscipit dignissim. Sed in massa vel nulla auctor hendrerit vel sit amet nunc. Maecenas auctor justo nec nunc sollicitudin, vitae hendrerit sapien lobortis. Donec placerat dolor ac elit lobortis, eget suscipit quam rutrum. Nulla at lacinia ante. In hac habitasse platea dictumst. Suspendisse vitae lectus vel mi pulvinar ultrices. Nam quis rutrum massa. Suspendisse ut mauris mi. Nulla laoreet vestibulum lacus, in luctus mauris malesuada in. Praesent ut odio ac felis vulputate pretium vel eu nibh. Proin auctor aliquam justo, sit amet ultricies nisl interdum vel. Maecenas et lobortis augue. Nulla facilisi. Sed maximus, arcu in feugiat lobortis, velit augue bibendum lectus, vel pharetra lectus nibh eu justo. Suspendisse non quam non diam faucibus efficitur eu vel leo. Sed eleifend metus non augue scelerisque bibendum. Nullam fringilla dolor non lorem suscipit interdum. Aenean euismod vel ante id feugiat. Donec pretium erat vel nulla aliquam, vitae feugiat odio vulputate. Morbi nec mauris sed enim blandit euismod. Fusce a lectus vitae risus malesuada facilisis id id mi. Fusce venenatis est nec purus vulputate, vel ultricies neque vulputate. Integer at sapien eget augue bibendum rutrum. Integer vel risus eget massa aliquam malesuada ac sed lectus. Donec euismod velit vel leo convallis, id volutpat nisl finibus. Vivamus tempor risus euismod, non faucibus augue dictum. Maecenas at velit non arcu consequat efficitur sit amet eu odio. Donec vel lorem eu neque lobortis vulputate vel vel eros. In eleifend libero eget eros elementum efficitur. Nulla facilisi. Fusce bibendum eget mi eu mattis. Sed luctus, odio ut gravida commodo, leo neque sagittis purus, nec bibendum est justo eget felis. Sed quis enim vel sapien mollis vulputate. Quisque euismod ex eget metus efficitur, non bibendum elit commodo. Integer non elit auctor, cursus ipsum ut, consectetur purus. Duis dapibus lacinia nibh, nec fermentum est consequat vitae. Aenean dignissim, augue sed gravida sollicitudin, sapien justo commodo risus, vitae tristique est tortor ut lectus. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Integer dapibus vestibulum lacus eu euismod. Praesent aliquet dapibus lacus, a dictum nulla faucibus ac. Proin ac lacus nunc. Sed at justo a ex sagittis rutrum in ac augue. Suspendisse potenti. Nulla facilisi. Nam vitae augue sapien. Aliquam erat volutpat. Vestibulum ullamcorper varius mauris, vel convallis est commodo sed. Sed interdum arcu at enim varius, id viverra ante mattis. Suspendisse eu nibh ligula. Morbi venenatis vehicula velit eu dictum. Nam at orci convallis, mollis dolor sed, eleifend orci. Sed sed bibendum velit. In tincidunt libero vel eros tincidunt, a pellentesque mauris venenatis. ..."
  private val madridTimezone = ZoneId.of("Europe/Madrid")
