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

import io.github.sovedus.socks5.common.{
  Command,
  CommandReplyType,
  Resolver,
  Socks5AddressHelper
}
import io.github.sovedus.socks5.common.Socks5Constants.*
import io.github.sovedus.socks5.common.Socks5Exception.{
  AuthenticationException,
  NoSupportedAuthMethodException,
  ProtocolVersionException,
  UnsupportedCommandException
}
import io.github.sovedus.socks5.common.auth.AuthenticationStatus
import io.github.sovedus.socks5.server.auth.ServerAuthenticator

import cats.effect.Async
import cats.syntax.all.*

import java.net.{ConnectException, UnknownHostException}

import fs2.Chunk
import fs2.io.net.Socket

private[server] class Socks5ServerConnectionHandler[F[_]: Async](
    private val authenticators: Map[Byte, ServerAuthenticator[F]],
    private val commands: Map[Command, Socks5ServerCommandHandler[F]],
    protected val socket: Socket[F],
    protected val resolver: Resolver[F]
) extends Socks5AddressHelper[F] {

  protected val F: Async[F] = implicitly

  def handle(): F[Unit] = {
    val f = for {
      authMethod <- handleHandshake()
      _ <- handleAuthentication(authMethod)
      _ <- handleCommand()
    } yield {}

    f.attemptT
      .leftSemiflatTap {
        case _: UnknownHostException =>
          CommandReply(CommandReplyType.HOST_UNREACHABLE, IPv4_ZERO, PORT_ZERO).send(socket)
        case _: ConnectException =>
          CommandReply(CommandReplyType.CONNECTION_REFUSED, IPv4_ZERO, PORT_ZERO).send(socket)
        case _: ProtocolVersionException =>
          CommandReply(CommandReplyType.GENERAL_SOCKS_SERVER_FAILURE, IPv4_ZERO, PORT_ZERO)
            .send(socket)
        case _: UnsupportedCommandException =>
          CommandReply(CommandReplyType.COMMAND_NOT_SUPPORTED, IPv4_ZERO, PORT_ZERO).send(
            socket)
        case _: AuthenticationException => F.unit
        case _ =>
          CommandReply(CommandReplyType.GENERAL_SOCKS_SERVER_FAILURE, IPv4_ZERO, PORT_ZERO)
            .send(socket)
      }
      .rethrowT
  }

  private def handleHandshake(): F[Byte] = for {
    bytes <- socket.readN(2).map(c => (c(0), c(1)))
    (version, nMethods) = bytes
    _ = if (nMethods <= 0)
      throw new IllegalArgumentException(
        s"Invalid number of authentication methods: $nMethods (must be positive)")
    _ <- checkProtocolVersion(version)
    methods <- socket.readN(nMethods.toInt).map(_.toArray)
    authMethod <- getFirstSuitableMethod(methods)
    _ <- sendSelectedAuthMethodReply(authMethod)
  } yield authMethod

  private def handleAuthentication(authMethod: Byte): F[Unit] = F.defer {
    authenticators
      .get(authMethod)
      .toOptionT
      .semiflatMap(_.authenticate(socket))
      .getOrRaise(new NoSuchElementException(
        s"Unsupported authentication method: 0x${authMethod.toInt.toHexString}"))
      .map(_ == AuthenticationStatus.SUCCESS)
      .ifM(F.unit, F.raiseError(AuthenticationException("User authentication failed")))
  }

  private def handleCommand(): F[Unit] = parseCommand().flatMap { req =>
    commands
      .getOrElse(req.command, throw UnsupportedCommandException(req.command.code))
      .handle(req.address, req.port)
      .evalTap(_ =>
        CommandReply(CommandReplyType.SUCCEEDED, req.address, req.port).send(socket))
      .use { transferPipe =>
        socket.reads.through(transferPipe).through(socket.writes).compile.drain
      }
  }

  private def sendSelectedAuthMethodReply(authMethod: Byte): F[Unit] =
    socket.write(Chunk(VERSION_SOCKS5_BYTE, authMethod)) >>
      F.raiseWhen(authMethod == NO_ACCEPTABLE_METHODS)(NoSupportedAuthMethodException)

  private def parseCommand(): F[CommandRequest] = for {
    bytes <- socket.readN(2).map(c => (c(0), c(1)))
    (version, cmd) = bytes
    _ <- checkProtocolVersion(version)
    command = Command(cmd)
    bytes <- socket.readN(2).map(c => (c(0), c(1)))
    (_, addressType) = bytes
    address <- parseAddress(addressType)
    port <- parsePort()
  } yield CommandRequest(command, address, port)

  private def getFirstSuitableMethod(authMethods: Array[Byte]): F[Byte] =
    F.delay(authMethods.find(authenticators.contains).getOrElse(NO_ACCEPTABLE_METHODS))

  private def checkProtocolVersion(version: Byte): F[Unit] =
    F.raiseError(ProtocolVersionException(version)).whenA(version != VERSION_SOCKS5)
}
