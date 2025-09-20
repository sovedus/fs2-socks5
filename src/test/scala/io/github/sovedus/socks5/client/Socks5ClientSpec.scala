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
import io.github.sovedus.socks5.common.CommandReplyType
import io.github.sovedus.socks5.common.CommandReplyType.*
import io.github.sovedus.socks5.common.Socks5Exception.{
  AuthenticationException,
  HandleCommandException,
  IncompleteReadException,
  NoSupportedAuthMethodException,
  ReachedEndOfStream
}
import io.github.sovedus.socks5.common.auth.UserPasswordCredential
import io.github.sovedus.socks5.test.utils.{Command, Handshake}

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxOptionId

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

import com.comcast.ip4s.IpLiteralSyntax
import fs2.Chunk
import fs2.concurrent.SignallingRef
import fs2.io.net.Network

class Socks5ClientSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers {

  private implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

  "Socks5Client" should "successfully complete CONNECT command with IPv4 address and bidirectional data exchange" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val handshakeResp = Handshake.Response.withoutAuthentication

    val commandReq = Command.Connect.Request.fromIpV4(ipv4"127.0.0.1", 443)
    val commandResp =
      Command.Connect.Response.fromIpV4(CommandReplyType.SUCCEEDED, ipv4"127.0.0.1", 443)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val client = Socks5ClientBuilder
          .default[IO]
          .withIdleTimeout(200.millis)
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .build

        for {
          clientFiber <- fs2.Stream
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
                _ <- serverSocket
                  .readN(handshakeReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(handshakeReq))
                _ <- serverSocket.write(Chunk.from(handshakeResp))
                _ <- serverSocket
                  .readN(commandReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(commandReq))
                _ <- serverSocket.write(Chunk.from(commandResp))
                _ <- serverSocket
                  .readN(testDataReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(testDataReq))
                _ <- serverSocket.write(Chunk.from(testDataResp))
                _ <- IO.sleep(100.millis)
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
            .start
          receiveData <- clientFiber.join.flatMap(_.embedError)
        } yield receiveData should equal(testDataResp)
    }
  }

  it should "successfully complete CONNECT command with domain and bidirectional data exchange" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val handshakeResp = Handshake.Response.withoutAuthentication

    val commandReq = Command.Connect.Request.fromDomain("example.local", 443)
    val commandResp =
      Command.Connect.Response.fromIpV4(CommandReplyType.SUCCEEDED, ipv4"127.0.0.1", 443)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val client = Socks5ClientBuilder
          .default[IO]
          .withIdleTimeout(200.millis)
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .build

        for {
          clientFiber <- fs2.Stream
            .emit(Chunk.from(testDataReq))
            .unchunks
            .through(client.connect(host"example.local", port"443"))
            .compile
            .toList
            .start
          signal <- SignallingRef[IO].of(false)
          _ <- serverStream
            .interruptWhen(signal)
            .evalMap { serverSocket =>
              for {
                _ <- serverSocket
                  .readN(handshakeReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(handshakeReq))
                _ <- serverSocket.write(Chunk.from(handshakeResp))
                _ <- serverSocket
                  .readN(commandReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(commandReq))
                _ <- serverSocket.write(Chunk.from(commandResp))
                _ <- serverSocket
                  .readN(testDataReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(testDataReq))
                _ <- serverSocket.write(Chunk.from(testDataResp))
                _ <- IO.sleep(100.millis)
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
            .start
          receiveData <- clientFiber.join.flatMap(_.embedError)
        } yield receiveData should equal(testDataResp)
    }
  }

  it should "successfully complete CONNECT command with IPv6 address and bidirectional data exchange" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val handshakeResp = Handshake.Response.withoutAuthentication

    val commandReq = Command.Connect.Request.fromIpV6(ipv6"2001:db8::1", 443)
    val commandResp =
      Command.Connect.Response.fromIpV6(CommandReplyType.SUCCEEDED, ipv6"2001:db8::1", 443)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val client = Socks5ClientBuilder
          .default[IO]
          .withIdleTimeout(200.millis)
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .build

        for {
          clientFiber <- fs2.Stream
            .emit(Chunk.from(testDataReq))
            .unchunks
            .through(client.connect(ipv6"2001:db8::1", port"443"))
            .compile
            .toList
            .start
          signal <- SignallingRef[IO].of(false)
          _ <- serverStream
            .interruptWhen(signal)
            .evalMap { serverSocket =>
              for {
                _ <- serverSocket
                  .readN(handshakeReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(handshakeReq))
                _ <- serverSocket.write(Chunk.from(handshakeResp))
                _ <- serverSocket
                  .readN(commandReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(commandReq))
                _ <- serverSocket.write(Chunk.from(commandResp))
                _ <- serverSocket
                  .readN(testDataReq.length)
                  .map(_.toList)
                  .asserting(_ should equal(testDataReq))
                _ <- serverSocket.write(Chunk.from(testDataResp))
                _ <- IO.sleep(100.millis)
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
            .start
          receiveData <- clientFiber.join.flatMap(_.embedError)
        } yield receiveData should equal(testDataResp)
    }
  }

  it should "successfully authenticate with username/password and complete CONNECT command" in {
    val handshakeReq = Handshake.Request.withUserPassword
    val handshakeResp = Handshake.Response.withUserPassword

    val user = "foo"
    val pass = "bar"

    val authReq = List[Byte](0x01, user.getBytes.length.toByte)
      .appendedAll(user.getBytes.toList)
      .appended(pass.getBytes.length.toByte)
      .appendedAll(pass.getBytes.toList)

    val authResp = List[Byte](0x01, 0x00)

    val commandReq = Command.Connect.Request.fromIpV4(ipv4"127.0.0.1", 443)
    val commandResp =
      Command.Connect.Response.fromIpV4(CommandReplyType.SUCCEEDED, ipv4"127.0.0.1", 443)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val credential = UserPasswordCredential(user, pass)
          val userPassAuthenticator = UserPasswordAuthenticator[IO](credential)

          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .withAuthenticator(userPassAuthenticator)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(3)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(handshakeResp))
                  _ <- serverSocket
                    .readN(authReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(authReq))
                  _ <- serverSocket.write(Chunk.from(authResp))
                  _ <- serverSocket
                    .readN(10)
                    .map(_.toList)
                    .asserting(_ should equal(commandReq))
                  _ <- serverSocket.write(Chunk.from(commandResp))
                  _ <- serverSocket
                    .readN(5)
                    .map(_.toList)
                    .asserting(_ should equal(testDataReq))
                  _ <- serverSocket.write(Chunk.from(testDataResp))
                  _ <- IO.sleep(100.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError)
          } yield {}
      }
      .assertNoException
  }

  it should "throw NoSupportedAuthMethodException when server responds with NO_ACCEPTABLE_METHODS (0xFF)" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val handshakeResp = Handshake.Response.withWrongAuthMethod

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO].serverResource(host"127.0.0.1".some).use {
      case (serverAddress, serverStream) =>
        val client = Socks5ClientBuilder
          .default[IO]
          .withIdleTimeout(200.millis)
          .withHost(serverAddress.host)
          .withPort(serverAddress.port)
          .build

        for {
          clientFiber <- fs2.Stream
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
                _ <- serverSocket.readN(3).map(_.toList).asserting(_ should equal(handshakeReq))
                _ <- serverSocket.write(Chunk.from(handshakeResp))
                _ <- IO.sleep(100.millis)
                _ <- signal.set(true)
              } yield {}
            }
            .compile
            .drain
            .start
          _ <- clientFiber.join
            .flatMap(_.embedError)
            .assertThrowsWithMessage[NoSupportedAuthMethodException.type](
              "No supported authentication method"
            )
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

    it should s"throw HandleCommandException for $replyName($code) CONNECT response" in {
      val handshakeReq = Handshake.Request.withoutAuthentication
      val handshakeResp = Handshake.Response.withoutAuthentication

      val commandReq = Command.Connect.Request.fromIpV4(ipv4"127.0.0.1", 443)
      val commandResp = Command.Connect.Response.fromIpV4(cmdReply, ipv4"127.0.0.1", 443)

      val testDataReq = List[Byte](1, 2, 3, 4, 5)

      Network[IO].serverResource(host"127.0.0.1".some).use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(3)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(handshakeResp))
                  _ <- serverSocket
                    .readN(10)
                    .map(_.toList)
                    .asserting(_ should equal(commandReq))
                  _ <- serverSocket.write(Chunk.from(commandResp))
                  _ <- IO.sleep(100.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError).assertThrows[HandleCommandException]
          } yield {}
      }
    }
  }

  it should "fail when username/password authentication returns non-zero status" in {
    val handshakeReq = Handshake.Request.withUserPassword
    val handshakeResp = Handshake.Response.withUserPassword

    val user = "foo"
    val pass = "bar"

    val authReq = List[Byte](0x01, user.getBytes.length.toByte)
      .appendedAll(user.getBytes.toList)
      .appended(pass.getBytes.length.toByte)
      .appendedAll(pass.getBytes.toList)

    val authResp = List[Byte](0x01, 0x01)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val credential = UserPasswordCredential(user, pass)
          val userPassAuthenticator = UserPasswordAuthenticator[IO](credential)

          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .withAuthenticator(userPassAuthenticator)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(3)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(handshakeResp))
                  _ <- serverSocket
                    .readN(authReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(authReq))
                  _ <- serverSocket.write(Chunk.from(authResp))
                  _ <- IO.sleep(100.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError)
          } yield {}
      }
      .assertThrows[AuthenticationException]
  }

  it should "handle empty username/password in authentication" in {
    val handshakeReq = Handshake.Request.withUserPassword
    val handshakeResp = Handshake.Response.withUserPassword

    val user = ""
    val pass = ""

    val authReq = List[Byte](0x01, user.getBytes.length.toByte)
      .appendedAll(user.getBytes.toList)
      .appended(pass.getBytes.length.toByte)
      .appendedAll(pass.getBytes.toList)

    val authResp = List[Byte](0x01, 0x01)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val credential = UserPasswordCredential(user, pass)
          val userPassAuthenticator = UserPasswordAuthenticator[IO](credential)

          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .withAuthenticator(userPassAuthenticator)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(3)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(handshakeResp))
                  _ <- serverSocket
                    .readN(authReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(authReq))
                  _ <- serverSocket.write(Chunk.from(authResp))
                  _ <- IO.sleep(100.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError)
          } yield {}
      }
      .assertThrowsWithMessage[IllegalArgumentException](
        "Username and password cannot be empty"
      )
  }

  it should "throw exception when server sends malformed handshake response" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val malformedHandshakeResp = List[Byte](0x05)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(3)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(malformedHandshakeResp))
                  _ <- IO.sleep(100.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError)
          } yield {}
      }
      .assertThrows[IncompleteReadException]
  }

  it should "throw exception when server sends malformed CONNECT response" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val handshakeResp = Handshake.Response.withoutAuthentication

    val commandReq = Command.Connect.Request.fromIpV4(ipv4"127.0.0.1", 443)
    val malformedCommandResp = Command.Connect.Response
      .fromIpV4(CommandReplyType.SUCCEEDED, ipv4"127.0.0.1", 443)
      .take(5)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(handshakeReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(handshakeResp))
                  _ <- serverSocket
                    .readN(commandReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(commandReq))
                  _ <- serverSocket.write(Chunk.from(malformedCommandResp))
                  _ <- IO.sleep(100.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            receiveData <- clientFiber.join.flatMap(_.embedError)
          } yield receiveData should equal(testDataResp)
      }
      .assertThrows[IncompleteReadException]
  }

  it should "handle server closing connection unexpectedly during handshake" in {
    val handshakeReq = Handshake.Request.withoutAuthentication

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(handshakeReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError)
          } yield {}
      }
      .assertThrows[ReachedEndOfStream]

  }

  it should "handle server closing connection unexpectedly during CONNECT" in {
    val handshakeReq = Handshake.Request.withoutAuthentication
    val handshakeResp = Handshake.Response.withoutAuthentication

    val commandReq = Command.Connect.Request.fromIpV4(ipv4"127.0.0.1", 443)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withIdleTimeout(200.millis)
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(handshakeReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- serverSocket.write(Chunk.from(handshakeResp))
                  _ <- serverSocket
                    .readN(commandReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(commandReq))
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            _ <- clientFiber.join.flatMap(_.embedError)
          } yield {}
      }
      .assertThrows[ReachedEndOfStream]
  }

  it should "throw TimeoutException when server does not respond to handshake within timeout" in {
    val handshakeReq = Handshake.Request.withoutAuthentication

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Network[IO]
      .serverResource(host"127.0.0.1".some)
      .use {
        case (serverAddress, serverStream) =>
          val client = Socks5ClientBuilder
            .default[IO]
            .withHost(serverAddress.host)
            .withPort(serverAddress.port)
            .withIdleTimeout(200.millis)
            .build

          for {
            clientFiber <- fs2.Stream
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
                  _ <- serverSocket
                    .readN(handshakeReq.length)
                    .map(_.toList)
                    .asserting(_ should equal(handshakeReq))
                  _ <- IO.sleep(500.millis)
                  _ <- signal.set(true)
                } yield {}
              }
              .compile
              .drain
              .start
            receiveData <- clientFiber.join.flatMap(_.embedError)
          } yield receiveData should equal(testDataResp)
      }
      .assertThrows[TimeoutException]
  }

  // TODO добавить тесты с таймаутами
}
