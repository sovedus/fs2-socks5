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

import io.github.sovedus.socks5.common.{Command, Resolver}
import io.github.sovedus.socks5.server.auth.ServerAuthenticator

import cats.effect.{Async, Resource}

import org.typelevel.log4cats.{Logger, LoggerFactory}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import com.comcast.ip4s.*
import fs2.io.net.Network

final class Socks5ServerBuilder[F[_]: Async: Network] private (
    val host: Option[Host],
    val port: Option[Port],
    val limitConnections: Int,
    val idleTimeout: FiniteDuration,
    private val authenticators: Map[Byte, ServerAuthenticator[F]],
    private val resolver: Resolver[F],
    private val errorHandler: ErrorHandler[F],
    private val commands: Map[Command, Socks5ServerCommandHandler[F]],
    private val logger: Logger[F]
) {
  def withHost(host: Host): Socks5ServerBuilder[F] = copy(host = Option(host))

  def withPort(port: Port): Socks5ServerBuilder[F] = copy(port = Option(port))

  def withAuthenticator(authenticator: ServerAuthenticator[F]): Socks5ServerBuilder[F] =
    copy(authenticators = authenticators.updated(authenticator.code, authenticator))

  def withResolver(resolver: Resolver[F]): Socks5ServerBuilder[F] = copy(resolver = resolver)

  def withLimitConnections(limitConnections: Int): Socks5ServerBuilder[F] = {
    assert(limitConnections > 0, "Limit connections must be greater than 0")

    copy(limitConnections = limitConnections)
  }

  def withErrorHandler(errorHandler: ErrorHandler[F]): Socks5ServerBuilder[F] =
    copy(errorHandler = errorHandler)

  def withConnectionHandler(handler: Socks5ServerCommandHandler[F]): Socks5ServerBuilder[F] =
    copy(commands = commands.updated(Command.CONNECT, handler))

  def withLogger(logger: Logger[F]): Socks5ServerBuilder[F] = copy(logger = logger)

  def withIdleTimeout(timeout: FiniteDuration): Socks5ServerBuilder[F] =
    copy(idleTimeout = timeout)

  def build: Resource[F, Socks5Server[F]] = Socks5Server.createAndStart(
    host,
    port,
    authenticators,
    resolver,
    limitConnections,
    errorHandler,
    commands,
    idleTimeout
  )

  private def copy(
      host: Option[Host] = this.host,
      port: Option[Port] = this.port,
      limitConnections: Int = this.limitConnections,
      authenticators: Map[Byte, ServerAuthenticator[F]] = this.authenticators,
      resolver: Resolver[F] = this.resolver,
      errorHandler: ErrorHandler[F] = this.errorHandler,
      commands: Map[Command, Socks5ServerCommandHandler[F]] = this.commands,
      logger: Logger[F] = this.logger,
      idleTimeout: FiniteDuration = this.idleTimeout
  ): Socks5ServerBuilder[F] = new Socks5ServerBuilder(
    host = host,
    port = port,
    limitConnections = limitConnections,
    authenticators = authenticators,
    resolver = resolver,
    errorHandler = errorHandler,
    commands = commands,
    logger = logger,
    idleTimeout = idleTimeout
  )
}

object Socks5ServerBuilder {

  def default[F[_]: Async: Network: LoggerFactory]: Socks5ServerBuilder[F] =
    new Socks5ServerBuilder(
      host = None,
      port = None,
      limitConnections = Int.MaxValue,
      authenticators = Map.empty,
      resolver = Resolver.default,
      errorHandler = ErrorHandler.default,
      commands = Map(Command.CONNECT -> new Socks5ServerConnectCommandHandler()),
      logger = LoggerFactory[F].getLoggerFromClass(Socks5Server.getClass),
      idleTimeout = 60.seconds
    )
}
