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

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

import com.comcast.ip4s.{IpAddress, IpLiteralSyntax, Port, SocketAddress}
import fs2.{Chunk, Pipe}
import fs2.io.net.Network

class Socks5ServerSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers {
  private implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

  "Socks5Server" should "respond with NO_ACCEPTABLE_METHODS when client requests unsupported authentication method" in {
    val handshakeReq = Handshake.Request.create(2.toByte)
    val handshakeResp = Handshake.Response.withNoAcceptableMethods

    Socks5ServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"0")
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
    val commandResp = List[Byte](5, 0, 0, 1, 127, 0, 0, 1, 1, -69)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Socks5ServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"0")
      .withAuthenticator(NoAuthAuthenticator())
      .withErrorHandler(ErrorHandler.noop)
      .withConnectionHandler(commandHandler(testDataResp))
      .build
      .use { server =>
        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
          for {
            _ <- clientSocket.write(Chunk.from(handshakeReq))
            _ <- clientSocket.readN(2).map(_.toList).asserting(_ should equal(handshakeResp))
            _ <- clientSocket.write(Chunk.from(commandReq))
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

  private def commandHandler(sendData: List[Byte]): Socks5ServerCommandHandler[IO] =
    new Socks5ServerCommandHandler[IO] {
      override def handle(
          targetIp: IpAddress,
          targetPort: Port
      ): Resource[IO, Pipe[IO, Byte, Byte]] =
        Resource
          .pure(fs2.Stream.emits(sendData).covary[IO])
          .map(stream => in => stream.concurrently(in.delete(_ => true)))
    }

}
