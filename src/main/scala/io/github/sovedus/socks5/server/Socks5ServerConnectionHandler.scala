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

import io.github.sovedus.socks5.common.*
import io.github.sovedus.socks5.common.Socks5Constants.*
import io.github.sovedus.socks5.common.Socks5Exception.*
import io.github.sovedus.socks5.common.auth.AuthenticationStatus
import io.github.sovedus.socks5.server.auth.ServerAuthenticator

import cats.effect.Async
import cats.syntax.all.*

import java.net.{ConnectException, UnknownHostException}

import com.comcast.ip4s.{IpAddress, SocketAddress}
import fs2.Chunk

private[server] class Socks5ServerConnectionHandler[F[_]](
    rw: ReadWriter[F],
    serverAddress: SocketAddress[IpAddress],
    resolver: Resolver[F],
    authenticators: Map[Byte, ServerAuthenticator[F]],
    commands: Map[Command, Socks5ServerCommandHandler[F]]
)(implicit F: Async[F]) {

  def handle(): F[Unit] = {
    val f = for {
      authMethod <- handleHandshake()
      _ <- handleAuthentication(authMethod)
      _ <- handleCommand()
    } yield {}

    f.attemptT.leftSemiflatTap {
      case _: UnknownHostException =>
        CommandReply(CommandReplyType.HOST_UNREACHABLE, serverAddress.host, serverAddress.port)
          .send(rw)
      case _: ConnectException =>
        CommandReply(
          CommandReplyType.CONNECTION_REFUSED,
          serverAddress.host,
          serverAddress.port
        ).send(rw)
      case _: ProtocolVersionException =>
        CommandReply(
          CommandReplyType.GENERAL_SOCKS_SERVER_FAILURE,
          serverAddress.host,
          serverAddress.port
        ).send(
          rw
        )
      case _: UnsupportedCommandException =>
        CommandReply(
          CommandReplyType.COMMAND_NOT_SUPPORTED,
          serverAddress.host,
          serverAddress.port
        ).send(rw)
      case _: AuthenticationException => F.unit
      case _ =>
        CommandReply(
          CommandReplyType.GENERAL_SOCKS_SERVER_FAILURE,
          serverAddress.host,
          serverAddress.port
        ).send(
          rw
        )
    }.rethrowT
  }

  private def handleHandshake(): F[Byte] = for {
    (version, nMethods) <- rw.read2
    _ = if (nMethods <= 0)
      throw new IllegalArgumentException(
        s"Invalid number of authentication methods: $nMethods (must be positive)"
      )
    _ <- checkProtocolVersion(version)
    methods <- rw.readN(nMethods.toInt).map(_.toArray)
    authMethod <- getFirstSuitableMethod(methods)
    _ <- sendSelectedAuthMethodReply(authMethod)
  } yield authMethod

  private def handleAuthentication(authMethod: Byte): F[Unit] = F.defer {
    authenticators
      .get(authMethod)
      .toOptionT
      .semiflatMap(_.authenticate(rw))
      .getOrRaise(
        new NoSuchElementException(
          s"Unsupported authentication method: 0x${authMethod.toInt.toHexString}"
        )
      )
      .map(_ == AuthenticationStatus.SUCCESS)
      .ifM(F.unit, F.raiseError(AuthenticationException("User authentication failed")))
  }

  private def handleCommand(): F[Unit] = parseCommand().flatMap { req =>
    commands
      .getOrElse(req.command, throw UnsupportedCommandException(req.command.code))
      .handle(req.address, req.port)
      .evalTap(_ =>
        CommandReply(CommandReplyType.SUCCEEDED, serverAddress.host, serverAddress.port)
          .send(rw)
      )
      .use { transferPipe => rw.reads.through(transferPipe).through(rw.writes).compile.drain }
  }

  private def sendSelectedAuthMethodReply(authMethod: Byte): F[Unit] =
    rw.write(Chunk(VERSION_SOCKS5, authMethod)) >>
      F.raiseWhen(authMethod == NO_ACCEPTABLE_METHODS)(NoSupportedAuthMethodException)

  private def parseCommand(): F[CommandRequest] = for {
    (version, cmd) <- rw.read2
    _ <- checkProtocolVersion(version)
    command = Command(cmd)
    (_, addressType) <- rw.read2
    address <- addressType match {
      case 0x01 => AddressUtils.readIPv4(rw)
      case 0x03 => AddressUtils.readDomain(rw).flatMap(resolver.resolve)
      case 0x04 => AddressUtils.readIPv6(rw)
      case aType => F.raiseError(UnsupportedAddressTypeException(aType))
    }
    port <- AddressUtils.readPort(rw)
  } yield CommandRequest(command, address, port)

  private def getFirstSuitableMethod(authMethods: Array[Byte]): F[Byte] =
    F.delay(authMethods.find(authenticators.contains).getOrElse(NO_ACCEPTABLE_METHODS))

  private def checkProtocolVersion(version: Byte): F[Unit] =
    F.raiseError(ProtocolVersionException(version)).whenA(version != VERSION_SOCKS5)
}
