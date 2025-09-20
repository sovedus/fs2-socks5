/*
 * Copyright 2025 Sovedus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.sovedus.socks5.server

import io.github.sovedus.socks5.server.auth.NoAuthAuthenticator
import io.github.sovedus.socks5.test.utils.Handshake

import cats.effect.{IO, Resource}
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.OptionValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

import com.comcast.ip4s.{IpAddress, IpLiteralSyntax, Port, SocketAddress}
import fs2.{Chunk, CompositeFailure, Pipe}
import fs2.io.net.Network

class Socks5ServerSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers with OptionValues {
  private implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

  "Socks5Server" should "respond with NO_ACCEPTABLE_METHODS when client requests unsupported authentication method" in {
    val handshakeReq = Handshake.Request.create(2.toByte)
    val handshakeResp = Handshake.Response.withNoAcceptableMethods

    Socks5ServerBuilder
      .default[IO]
      .withIdleTimeout(200.millis)
      .withHost(host"localhost")
      .withAuthenticator(NoAuthAuthenticator())
      .withErrorHandler(ErrorHandler.noop)
      .build
      .use { server =>
        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
          for {
            _ <- clientSocket.write(Chunk.from(handshakeReq))
            _ <- clientSocket.readN(2).map(_.toList).asserting(_ should equal(handshakeResp))
          } yield {}
        }
      }
  }

  it should "handle CONNECT command with end-to-end data transfer" in {
    val handshakeReq = List[Byte](5, 1, 0)
    val handshakeResp = List[Byte](5, 0)

    val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
    val commandRespWithoutPort = List[Byte](5, 0, 0, 1, 127, 0, 0, 1)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.map(_ + 1).map(_.toByte)

    Socks5ServerBuilder
      .default[IO]
      .withIdleTimeout(200.millis)
      .withHost(host"localhost")
      .withAuthenticator(NoAuthAuthenticator())
      .withErrorHandler(ErrorHandler.noop)
      .withConnectionHandler(commandHandler())
      .build
      .use { server =>
        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
          for {
            _ <- clientSocket.write(Chunk.from(handshakeReq))
            _ <- clientSocket.readN(2).map(_.toList).asserting(_ should equal(handshakeResp))
            _ <- clientSocket.write(Chunk.from(commandReq))
            commandResp = commandRespWithoutPort ++ List(
              (server.port.value >> 8).toByte,
              server.port.value.toByte
            )
            _ <- clientSocket.readN(10).map(_.toList).asserting(_ should equal(commandResp))
            _ <- clientSocket.write(Chunk.from(testDataReq))
            _ <- clientSocket
              .readN(testDataResp.length)
              .map(_.toList)
              .asserting(_ should equal(testDataResp))
          } yield {}
        }
      }
  }

  it should "throw TimeoutException when client does not send data for longer than idle timeout" in {
    val handshakeReq = List[Byte](5, 1, 0)
    val handshakeResp = List[Byte](5, 0)

    val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
    val commandRespWithoutPort = List[Byte](5, 0, 0, 1, 127, 0, 0, 1)

    val testDataReqPart1 = List[Byte](1, 2, 3)

    val res = for {
      error <- Resource.eval(IO.deferred[Throwable])
      server <- Socks5ServerBuilder
        .default[IO]
        .withIdleTimeout(200.millis)
        .withHost(host"localhost")
        .withAuthenticator(NoAuthAuthenticator())
        .withErrorHandler(ex => error.complete(ex).void)
        .withConnectionHandler(commandHandler())
        .build
      clientSocket <- Network[IO].client(SocketAddress(server.host, server.port))
    } yield (server, clientSocket, error)

    res.use {
      case (server, clientSocket, error) =>
        for {
          _ <- clientSocket.write(Chunk.from(handshakeReq))
          _ <- clientSocket.readN(2).map(_.toList).asserting(_ should equal(handshakeResp))
          _ <- clientSocket.write(Chunk.from(commandReq))
          commandResp = commandRespWithoutPort ++ List(
            (server.port.value >> 8).toByte,
            server.port.value.toByte
          )
          _ <- clientSocket.readN(10).map(_.toList).asserting(_ should equal(commandResp))
          _ <- clientSocket.write(Chunk.from(testDataReqPart1))
          _ <- IO.sleep(500.millis)
          err <- error.tryGet
        } yield err.value match {
          case _: TimeoutException => succeed
          case ex: CompositeFailure if ex.all.forall(_.isInstanceOf[TimeoutException]) =>
            succeed
          case ex => fail(ex)
        }
    }
  }

  private def commandHandler(): Socks5ServerCommandHandler[IO] =
    new Socks5ServerCommandHandler[IO] {
      override def handle(
          targetIp: IpAddress,
          targetPort: Port
      ): Resource[IO, Pipe[IO, Byte, Byte]] = {
        val pipe: fs2.Pipe[IO, Byte, Byte] = _.map(b => (b + 1).toByte)

        Resource.pure(pipe)
      }
    }

}
