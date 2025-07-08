package com.github.sovedus.socks5.server

import cats.effect.{Async, Resource}
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.implicits.catsSyntaxOptionId
import com.comcast.ip4s.{Host, Port}
import com.github.sovedus.socks5.common.{Command, Resolver}
import com.github.sovedus.socks5.server.auth.ServerAuthenticator
import fs2.io.net.Network

case class Socks5Server[F[_]: Async] private (
    host: Host,
    port: Port,
    limitConnections: Int,
    errorHandler: ErrorHandler[F],
    commands: Map[Command, Socks5ServerCommandHandler[F]]
)

object Socks5Server {

  private[server] def createAndStart[F[_]: Async: Network](
      host: Host,
      port: Port,
      authenticators: Map[Byte, ServerAuthenticator[F]],
      resolver: Resolver[F],
      limitConnections: Int,
      errorHandler: ErrorHandler[F],
      commands: Map[Command, Socks5ServerCommandHandler[F]]
  ): Resource[F, Socks5Server[F]] =
    for {
      server <- Network[F].serverResource(host.some, port.some)
      (serverAddress, serverSockets) = server
      _ <- serverSockets
        .map { clientSocket =>
          fs2
            .Stream
            .eval(
              new Socks5ServerConnectionHandler(
                authenticators,
                commands,
                clientSocket,
                resolver).handle().handleErrorWith(errorHandler.handleException).voidError)
        }
        .parJoin(limitConnections)
        .compile
        .drain
        .background
    } yield Socks5Server(
      serverAddress.host,
      serverAddress.port,
      limitConnections,
      errorHandler: ErrorHandler[F],
      commands)

}
