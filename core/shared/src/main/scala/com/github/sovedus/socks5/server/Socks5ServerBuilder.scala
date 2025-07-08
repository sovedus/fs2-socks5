package com.github.sovedus.socks5.server

import cats.effect.{Async, Resource}
import com.comcast.ip4s._
import com.github.sovedus.socks5.common.{Command, Resolver}
import com.github.sovedus.socks5.server.auth.ServerAuthenticator
import fs2.io.net.Network

class Socks5ServerBuilder[F[_]: Async: Network] private (
    val host: Host,
    val port: Port,
    val limitConnections: Int,
    private val authenticators: Map[Byte, ServerAuthenticator[F]],
    private val resolver: Resolver[F],
    private val errorHandler: ErrorHandler[F],
    private val commands: Map[Command, Socks5ServerCommandHandler[F]]
) {
  def withHost(host: Host): Socks5ServerBuilder[F] = copy(host = host)

  def withPort(port: Port): Socks5ServerBuilder[F] = copy(port = port)

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

  def build: Resource[F, Socks5Server[F]] = Socks5Server.createAndStart(
    host,
    port,
    authenticators,
    resolver,
    limitConnections,
    errorHandler,
    commands)

  private def copy(
      host: Host = this.host,
      port: Port = this.port,
      limitConnections: Int = this.limitConnections,
      authenticators: Map[Byte, ServerAuthenticator[F]] = this.authenticators,
      resolver: Resolver[F] = this.resolver,
      errorHandler: ErrorHandler[F] = this.errorHandler,
      commands: Map[Command, Socks5ServerCommandHandler[F]] = this.commands
  ): Socks5ServerBuilder[F] = new Socks5ServerBuilder(
    host = host,
    port = port,
    limitConnections = limitConnections,
    authenticators = authenticators,
    resolver = resolver,
    errorHandler = errorHandler,
    commands = commands)
}

object Socks5ServerBuilder {

  def default[F[_]: Async: Network] = new Socks5ServerBuilder(
    host = host"localhost",
    port = port"1080",
    limitConnections = Int.MaxValue,
    authenticators = Map.empty,
    resolver = Resolver.default,
    errorHandler = ErrorHandler.stderr,
    commands = Map(Command.CONNECT -> new Socks5ServerConnectCommandHandler())
  )
}
