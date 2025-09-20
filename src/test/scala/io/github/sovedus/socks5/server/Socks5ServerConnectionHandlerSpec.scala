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

import io.github.sovedus.socks5.common.{Command, CommandReplyType, ReadWriter, Resolver}
import io.github.sovedus.socks5.common.Socks5Constants.VERSION_SOCKS5
import io.github.sovedus.socks5.common.Socks5Exception.{
  AuthenticationException,
  ProtocolVersionException,
  UnsupportedCommandException
}
import io.github.sovedus.socks5.common.auth.AuthenticationStatus
import io.github.sovedus.socks5.common.auth.AuthenticationStatus.FAILURE
import io.github.sovedus.socks5.server.auth.{NoAuthAuthenticator, ServerAuthenticator}

import cats.effect.{IO, Resource}
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalamock.stubs.CatsEffectStubs
import org.scalatest.OptionValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.UnknownHostException

import com.comcast.ip4s.{
  IpAddress,
  IpLiteralSyntax,
  Ipv4Address,
  Ipv6Address,
  Port,
  SocketAddress
}
import fs2.{Chunk, Pipe}
import fs2.io.net.ConnectException

class Socks5ServerConnectionHandlerSpec
    extends AsyncFlatSpec
    with CatsEffectStubs
    with AsyncIOSpec
    with Matchers
    with OptionValues {

  "Socks5ServerConnectionHandler" should "throw IllegalArgumentException for negative number of authentication methods" in {
    val rwStub = stub[ReadWriter[IO]]

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](rwStub, serverAddress, null, Map.empty, Map.empty)

    val protocolVersion: Byte = 0x05
    val negativeNMethods: Byte = -1

    for {
      _ <- rwStub.read2.succeedsWith((protocolVersion, negativeNMethods))
      _ <- (rwStub.write _).succeedsWith(())
      _ <- handler
        .handle()
        .assertThrowsWithMessage[IllegalArgumentException](
          "Invalid number of authentication methods: -1 (must be positive)"
        )
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.GENERAL_SOCKS_SERVER_FAILURE.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        127,
        0,
        0,
        1,
        (serverAddress.port.value >> 8).toByte,
        serverAddress.port.value.toByte
      )
    )
  }

  it should "respond with chosen auth method code then throw exception on authentication failure" in {
    val rwStub = stub[ReadWriter[IO]]

    val failServerAuthenticator = new ServerAuthenticator[IO] {
      override def code: Byte = 0x02

      override def authenticate(rw: ReadWriter[IO]): IO[AuthenticationStatus] = IO(FAILURE)
    }

    val authenticators = Map(
      failServerAuthenticator.code -> failServerAuthenticator
    )

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](
        rwStub,
        serverAddress,
        null,
        authenticators,
        Map.empty
      )

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- rwStub.read2.succeedsWith((protocolVersion, NMethods))
      _ <- (rwStub.readN _).succeedsWith(Chunk(failServerAuthenticator.code))
      _ <- (rwStub.write _).succeedsWith(())
      _ <- handler
        .handle()
        .assertThrowsWithMessage[AuthenticationException]("User authentication failed")
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(Chunk(VERSION_SOCKS5, failServerAuthenticator.code))
  }

  it should "handle resolver failure by sending HOST_UNREACHABLE response and throwing UnknownHostException" in {
    val rwStub = stub[ReadWriter[IO]]
    val commandHandlerStub = stub[Socks5ServerCommandHandler[IO]]

    val resolver = Resolver.default[IO]

    val noAuthAuthenticator = NoAuthAuthenticator[IO]()

    val authenticators = Map(
      noAuthAuthenticator.code -> noAuthAuthenticator
    )

    val commands: Map[Command, Socks5ServerCommandHandler[IO]] = Map(
      Command.CONNECT -> commandHandlerStub
    )

    val host = "example.local"

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](
        rwStub,
        serverAddress,
        resolver,
        authenticators,
        commands
      )

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- rwStub.read1.succeedsWith(host.length.toByte)
      _ <- rwStub.read2.returnsIOOnCall {
        case 1 => IO((protocolVersion, NMethods))
        case 2 => IO((protocolVersion, Command.CONNECT.code))
        case 3 => IO((0x0 /*rsv*/, 0x03 /*Domain*/ ))
      }
      _ <- (rwStub.readN _).returnsIOOnCall {
        case 1 => IO(Chunk[Byte](noAuthAuthenticator.code))
        case 2 => IO(Chunk.array(host.getBytes))
      }
      _ <- (rwStub.write _).succeedsWith(())
      _ <- handler.handle().assertThrows[UnknownHostException]
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](VERSION_SOCKS5, noAuthAuthenticator.code),
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.HOST_UNREACHABLE.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        127,
        0,
        0,
        1,
        (serverAddress.port.value >> 8).toByte,
        serverAddress.port.value.toByte
      )
    )
  }

  it should "handle command handler failure by sending CONNECTION_REFUSED response and throwing ConnectException" in {
    val rwStub = stub[ReadWriter[IO]]

    val resolver = Resolver.default[IO]

    val noAuthAuthenticator = NoAuthAuthenticator[IO]()

    val authenticators = Map(
      noAuthAuthenticator.code -> noAuthAuthenticator
    )

    val failCommandHandler = new Socks5ServerCommandHandler[IO] {
      override def handle(
          targetIp: IpAddress,
          targetPort: Port
      ): Resource[IO, Pipe[IO, Byte, Byte]] =
        Resource.raiseError[IO, Pipe[IO, Byte, Byte], Throwable](new ConnectException())
    }

    val commands: Map[Command, Socks5ServerCommandHandler[IO]] = Map(
      Command.CONNECT -> failCommandHandler
    )

    val host = Ipv6Address.fromString("::1").value
    val port = 8080

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](
        rwStub,
        serverAddress,
        resolver,
        authenticators,
        commands
      )

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- rwStub.read2.returnsIOOnCall {
        case 1 => IO((protocolVersion, NMethods))
        case 2 => IO((protocolVersion, Command.CONNECT.code))
        case 3 => IO((0x0 /*rsv*/, 0x04 /*IPv6*/ ))
        case 4 => IO(((port >> 8).toByte, port.toByte))
      }
      _ <- (rwStub.readN _).returnsIOOnCall {
        case 1 => IO(Chunk[Byte](noAuthAuthenticator.code))
        case 2 => IO(Chunk.array(host.toBytes))
      }
      _ <- (rwStub.write _).succeedsWith(())
      _ <- handler.handle().assertThrows[ConnectException]
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](VERSION_SOCKS5, noAuthAuthenticator.code),
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.CONNECTION_REFUSED.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        127,
        0,
        0,
        1,
        (serverAddress.port.value >> 8).toByte,
        serverAddress.port.value.toByte
      )
    )
  }

  it should "reject connection with GENERAL_SOCKS_SERVER_FAILURE and throw ProtocolVersionException for wrong SOCKS version" in {
    val rwStub = stub[ReadWriter[IO]]

    val resolver = Resolver.default[IO]

    val noAuthAuthenticator = NoAuthAuthenticator[IO]()

    val authenticators = Map(
      noAuthAuthenticator.code -> noAuthAuthenticator
    )

    val failCommandHandler = new Socks5ServerCommandHandler[IO] {
      override def handle(
          targetIp: IpAddress,
          targetPort: Port
      ): Resource[IO, Pipe[IO, Byte, Byte]] =
        Resource.raiseError[IO, Pipe[IO, Byte, Byte], Throwable](new ConnectException())
    }

    val commands: Map[Command, Socks5ServerCommandHandler[IO]] = Map(
      Command.CONNECT -> failCommandHandler
    )

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](
        rwStub,
        serverAddress,
        resolver,
        authenticators,
        commands
      )

    val badProtocolVersion: Byte = 0x06
    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- rwStub.read2.returnsIOOnCall { case 1 => IO((badProtocolVersion, NMethods)) }
      _ <- (rwStub.write _).succeedsWith(())
      _ <- handler.handle().assertThrows[ProtocolVersionException]
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.GENERAL_SOCKS_SERVER_FAILURE.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        127,
        0,
        0,
        1,
        (serverAddress.port.value >> 8).toByte,
        serverAddress.port.value.toByte
      )
    )
  }

  it should "send COMMAND_NOT_SUPPORTED response and throw UnsupportedCommandException for unsupported command" in {
    val rwStub = stub[ReadWriter[IO]]
    val failCommandHandlerStub = stub[Socks5ServerCommandHandler[IO]]

    val resolver = Resolver.default[IO]

    val noAuthAuthenticator = NoAuthAuthenticator[IO]()

    val authenticators = Map(
      noAuthAuthenticator.code -> noAuthAuthenticator
    )

    val commands: Map[Command, Socks5ServerCommandHandler[IO]] = Map(
      Command.CONNECT -> failCommandHandlerStub
    )

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](
        rwStub,
        serverAddress,
        resolver,
        authenticators,
        commands
      )

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1
    val badCommandCode: Byte = 0x08

    for {
      _ <- rwStub.read2.returnsIOOnCall {
        case 1 => IO((protocolVersion, NMethods))
        case 2 => IO((protocolVersion, badCommandCode))
      }
      _ <- (rwStub.readN _).succeedsWith(Chunk[Byte](noAuthAuthenticator.code))
      _ <- (rwStub.write _).succeedsWith(())
      _ <- (failCommandHandlerStub.handle _).returnsIOWith(
        Resource.raiseError[IO, Pipe[IO, Byte, Byte], Throwable](new ConnectException())
      )
      _ <- handler.handle().assertThrows[UnsupportedCommandException]
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](VERSION_SOCKS5, noAuthAuthenticator.code),
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.COMMAND_NOT_SUPPORTED.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        127,
        0,
        0,
        1,
        (serverAddress.port.value >> 8).toByte,
        serverAddress.port.value.toByte
      )
    )
  }

  it should "throw UnsupportedCommandException and send COMMAND_NOT_SUPPORTED when no handler registered for requested command" in {
    val rwStub = stub[ReadWriter[IO]]

    val resolver = Resolver.default[IO]

    val noAuthAuthenticator = NoAuthAuthenticator[IO]()

    val authenticators = Map(
      noAuthAuthenticator.code -> noAuthAuthenticator
    )

    val host = Ipv4Address.fromString("127.0.0.1").value
    val port = 8080

    val serverAddress = SocketAddress(ipv4"127.0.0.1", port"8080")
    val handler =
      new Socks5ServerConnectionHandler[IO](
        rwStub,
        serverAddress,
        resolver,
        authenticators,
        Map.empty
      )

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- rwStub.read2.returnsIOOnCall {
        case 1 => IO((protocolVersion, NMethods))
        case 2 => IO((protocolVersion, Command.CONNECT.code))
        case 3 => IO((0x0 /*rsv*/, 0x01 /*IPv4*/ ))
        case 4 => IO(((port >> 8).toByte, port.toByte))
      }
      _ <- (rwStub.readN _).returnsIOOnCall {
        case 1 => IO(Chunk[Byte](noAuthAuthenticator.code))
        case 2 => IO(Chunk.array(host.toBytes))
      }
      _ <- (rwStub.write _).succeedsWith(())
      _ <- handler.handle().assertThrows[UnsupportedCommandException]
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](VERSION_SOCKS5, noAuthAuthenticator.code),
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.COMMAND_NOT_SUPPORTED.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        127,
        0,
        0,
        1,
        (serverAddress.port.value >> 8).toByte,
        serverAddress.port.value.toByte
      )
    )
  }

  // TODO добавить тесты с таймаутами
}
