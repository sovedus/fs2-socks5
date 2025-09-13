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

import io.github.sovedus.socks5.common.{Command, CommandReplyType, Resolver}
import io.github.sovedus.socks5.common.Socks5Constants.{PORT_ZERO, VERSION_SOCKS5_BYTE}
import io.github.sovedus.socks5.common.Socks5Exception.AuthenticationException
import io.github.sovedus.socks5.common.auth.AuthenticationStatus
import io.github.sovedus.socks5.common.auth.AuthenticationStatus.FAILURE
import io.github.sovedus.socks5.server.auth.{NoAuthAuthenticator, ServerAuthenticator}

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalamock.stubs.CatsEffectStubs
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.UnknownHostException

import fs2.Chunk
import fs2.io.net.Socket

class Socks5ServerConnectionHandlerSpec
    extends AsyncFlatSpec
    with CatsEffectStubs
    with AsyncIOSpec
    with Matchers {

  "Socks5ServerConnectionHandler" should "throw IllegalArgumentException for negative number of authentication methods" in {
    val socketStub = stub[Socket[IO]]

    val handler = new Socks5ServerConnectionHandler[IO](Map.empty, Map.empty, socketStub, null)

    val protocolVersion: Byte = 0x05
    val negativeNMethods: Byte = -1

    for {
      _ <- (socketStub.readN _).succeedsWith(Chunk[Byte](protocolVersion, negativeNMethods))
      _ <- handler
        .handle()
        .assertThrowsWithMessage[IllegalArgumentException](
          "Invalid number of authentication methods: -1 (must be positive)")
    } yield {}
  }

  it should "respond with chosen auth method code then throw exception on authentication failure" in {
    val socketStub = stub[Socket[IO]]

    val failServerAuthenticator = new ServerAuthenticator[IO] {
      override def code: Byte = 0x02

      override def authenticate(socket: Socket[IO]): IO[AuthenticationStatus] = IO(FAILURE)
    }

    val authenticators = Map(
      failServerAuthenticator.code -> failServerAuthenticator
    )

    val handler =
      new Socks5ServerConnectionHandler[IO](authenticators, Map.empty, socketStub, null)

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- (socketStub.readN _).returnsIOOnCall {
        case 1 => IO(Chunk[Byte](protocolVersion, NMethods))
        case 2 => IO(Chunk[Byte](failServerAuthenticator.code))
      }
      _ <- (socketStub.write _).succeedsWith(())
      _ <- handler
        .handle()
        .assertThrowsWithMessage[AuthenticationException]("User authentication failed")
      calls <- (socketStub.write _).callsIO
    } yield calls shouldBe List(Chunk(VERSION_SOCKS5_BYTE, failServerAuthenticator.code))
  }

  it should "handle resolver failure by sending HOST_UNREACHABLE response and throwing UnknownHostException" in {
    val socketStub = stub[Socket[IO]]
    val resolverStub = stub[Resolver[IO]]

    val noAuthAuthenticator = NoAuthAuthenticator[IO]()

    val authenticators = Map(
      noAuthAuthenticator.code -> noAuthAuthenticator
    )

    val host = "example.local"

    val handler =
      new Socks5ServerConnectionHandler[IO](authenticators, Map.empty, socketStub, resolverStub)

    val protocolVersion: Byte = 0x05
    val NMethods: Byte = 1

    for {
      _ <- (socketStub.readN _).returnsIOOnCall {
        case 1 => IO(Chunk[Byte](protocolVersion, NMethods))
        case 2 => IO(Chunk[Byte](noAuthAuthenticator.code))
        case 3 => IO(Chunk[Byte](protocolVersion, Command.CONNECT.code))
        case 4 => IO(Chunk[Byte](0x0 /*rsv*/, 0x03 /*Domain*/ ))
      }
      _ <- (socketStub.read _).returnsIOOnCall {
        case 1 => IO(Some(Chunk[Byte](host.length.toByte)))
        case 2 => IO(Some(Chunk.array(host.getBytes)))
      }
      _ <- (socketStub.write _).succeedsWith(())
      _ <- (resolverStub.resolve _).raisesErrorWith(new UnknownHostException())
      _ <- handler.handle().assertThrows[UnknownHostException]
      calls <- (socketStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](VERSION_SOCKS5_BYTE, noAuthAuthenticator.code),
      Chunk[Byte](
        protocolVersion,
        CommandReplyType.HOST_UNREACHABLE.code,
        0x00.toByte /*rsv*/,
        0x01 /*IPv4*/,
        0,
        0,
        0,
        0,
        (PORT_ZERO.value >> 8).toByte,
        PORT_ZERO.value.toByte)
    )
  }
}
