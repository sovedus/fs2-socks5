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

package io.github.sovedus.socks5.client

import io.github.sovedus.socks5.client.auth.UserPasswordAuthenticator
import io.github.sovedus.socks5.common.CommandReplyType.*
import io.github.sovedus.socks5.common.Socks5Exception.{
  HandleCommandException,
  NoSupportedAuthMethodException
}

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId

import com.comcast.ip4s.*
import fs2.Chunk
import fs2.concurrent.SignallingRef
import fs2.io.net.Network
import munit.CatsEffectSuite
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

class Socks5ClientSuite extends CatsEffectSuite {

  private implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

  test("should successfully complete data exchange using CONNECT command") {
    val handshakeReq = List[Byte](5, 1, 0)
    val handshakeResp = List[Byte](5, 0)

    val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
    val commandResp = List[Byte](5, 0, 0, 1, 127, 0, 0, 1, 1, -69)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val client = Socks5ClientBuilder
          .default[IO]
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .build

        for {
          clientFiber <- fs2
            .Stream
            .emit(Chunk.from(testDataReq))
            .unchunks
            .through(client.connect(ipv4"127.0.0.1", port"443"))
            .compile
            .toList
            .start
          signal <- SignallingRef[IO].of(false)
          _ <- serverStream
            .interruptWhen(signal)
            .evalMap { serverSocket =>
              for {
                _ <- serverSocket.readN(3).map(_.toList).assertEquals(handshakeReq)
                _ <- serverSocket.write(Chunk.from(handshakeResp))
                _ <- serverSocket.readN(10).map(_.toList).assertEquals(commandReq)
                _ <- serverSocket.write(Chunk.from(commandResp))
                _ <- serverSocket.readN(5).map(_.toList).assertEquals(testDataReq)
                _ <- serverSocket.write(Chunk.from(testDataResp))
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
          receiveData <- clientFiber.join.flatMap(_.embedError)
        } yield assertEquals(receiveData, testDataResp)
    }
  }

  test("should send correct user/password authentication request") {
    val handshakeReq = List[Byte](5, 1, 2)
    val handshakeResp = List[Byte](5, 2)

    val user = "foo"
    val pass = "bar"

    val authReq = List[Byte](0x01, user.getBytes.length.toByte)
      .appendedAll(user.getBytes.toList)
      .appended(pass.getBytes.length.toByte)
      .appendedAll(pass.getBytes.toList)

    val authResp = List[Byte](0x01, 0x00)

    val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
    val commandResp = Chunk[Byte](5, 0, 0, 1, 0, 0, 0, 0, 0, 0)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val userPassAuthenticator = UserPasswordAuthenticator[IO](user, pass)

        val client = Socks5ClientBuilder
          .default[IO]
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .withAuthenticator(userPassAuthenticator)
          .build

        for {
          clientFiber <- fs2
            .Stream
            .emit(Chunk.from(testDataReq))
            .unchunks
            .through(client.connect(ipv4"127.0.0.1", port"443"))
            .compile
            .toList
            .start
          signal <- SignallingRef[IO].of(false)
          _ <- serverStream
            .interruptWhen(signal)
            .evalMap { serverSocket =>
              for {
                _ <- serverSocket.readN(3).map(_.toList).assertEquals(handshakeReq)
                _ <- serverSocket.write(Chunk.from(handshakeResp))
                _ <- serverSocket.readN(authReq.length).map(_.toList).assertEquals(authReq)
                _ <- serverSocket.write(Chunk.from(authResp))
                _ <- serverSocket.readN(10).map(_.toList).assertEquals(commandReq)
                _ <- serverSocket.write(commandResp)
                _ <- serverSocket.readN(5).map(_.toList).assertEquals(testDataReq)
                _ <- serverSocket.write(Chunk.from(testDataResp))
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
          _ <- clientFiber.join.flatMap(_.embedError)
        } yield {}
    }
  }

  test(
    "should throw AuthenticationException when server return NO_ACCEPTABLE_METHODS at handshake") {
    val handshakeReq = List[Byte](5, 1, 0)
    val handshakeResp = Chunk[Byte](5, -1)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val client = Socks5ClientBuilder
          .default[IO]
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .build

        for {
          clientFiber <- fs2
            .Stream
            .emit(Chunk.from(testDataReq))
            .unchunks
            .through(client.connect(ipv4"127.0.0.1", port"443"))
            .compile
            .toList
            .start
          signal <- SignallingRef[IO].of(false)
          _ <- serverStream
            .interruptWhen(signal)
            .evalMap { serverSocket =>
              for {
                _ <- serverSocket.readN(3).map(_.toList).assertEquals(handshakeReq)
                _ <- serverSocket.write(handshakeResp)
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
          _ <- clientFiber
            .join
            .flatMap(_.embedError)
            .interceptMessage[NoSupportedAuthMethodException.type](
              "No supported authentication method")
        } yield {}
    }
  }

  List(
    GENERAL_SOCKS_SERVER_FAILURE,
    CONNECTION_NOT_ALLOWED,
    NETWORK_UNREACHABLE,
    HOST_UNREACHABLE,
    CONNECTION_REFUSED,
    TTL_EXPIRED,
    COMMAND_NOT_SUPPORTED,
    ADDRESS_TYPE_NOT_SUPPORTED,
    UNASSIGNED(0x09),
    UNASSIGNED(0xff.toByte)
  ).foreach { cmdReply =>
    val code = cmdReply.code
    val replyName = cmdReply.getClass.getSimpleName.replace("$", "")

    test(
      s"CONNECT command should throw HandleCommandException when server return $replyName($code) code") {
      val handshakeReq = List[Byte](5, 1, 0)
      val handshakeResp = Chunk[Byte](5, 0)

      val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
      val commandResp = Chunk[Byte](5, code, 0, 1, 0, 0, 0, 0, 0, 0)

      val testDataReq = List[Byte](1, 2, 3, 4, 5)

      Network[IO].serverResource(host"127.0.0.1".some).use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .build

          for {
            clientFiber <- fs2
              .Stream
              .emit(Chunk.from(testDataReq))
              .unchunks
              .through(client.connect(ipv4"127.0.0.1", port"443"))
              .compile
              .toList
              .start
            signal <- SignallingRef[IO].of(false)
            _ <- serverStream
              .interruptWhen(signal)
              .evalMap { serverSocket =>
                for {
                  _ <- serverSocket.readN(3).map(_.toList).assertEquals(handshakeReq)
                  _ <- serverSocket.write(handshakeResp)
                  _ <- serverSocket.readN(10).map(_.toList).assertEquals(commandReq)
                  _ <- serverSocket.write(commandResp)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
            _ <- clientFiber.join.flatMap(_.embedError).intercept[HandleCommandException]
          } yield {}
      }
    }

  }
}
