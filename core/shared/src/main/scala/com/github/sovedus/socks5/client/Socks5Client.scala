package com.github.sovedus.socks5.client

import cats.effect.Async
import cats.syntax.all._
import com.comcast.ip4s.{Host, Port, SocketAddress}
import com.github.sovedus.socks5.client.auth.ClientAuthenticator
import com.github.sovedus.socks5.common.Resolver
import fs2.Pipe
import fs2.io.net.{Network, Socket}

trait Socks5Client[F[_]] {
  def connect(host: Host, port: Port): fs2.Pipe[F, Byte, Byte]
}

private[client] object Socks5Client {

  def create[F[_]: Async: Network](
      host: Host,
      port: Port,
      authenticators: Map[Byte, ClientAuthenticator[F]],
      resolver: Resolver[F],
      resolveHostOnServer: Boolean
  ): Socks5Client[F] = new Socks5Client[F] {
    override def connect(targetHost: Host, targetPort: Port): Pipe[F, Byte, Byte] =
      createPipe(targetHost, targetPort) { socket =>
        Socks5ClientConnectCommandHandler(socket, authenticators, resolver, resolveHostOnServer)
      }

    private def createPipe[H <: Socks5ClientCommandHandler[F]](
        targetHost: Host,
        targetPort: Port
    )(cmdHandler: Socket[F] => F[H]): Pipe[F, Byte, Byte] = { in =>
      fs2
        .Stream
        .resource(Network[F].client(SocketAddress(host, port)))
        .evalMap(socket => cmdHandler(socket).flatMap(_.handle(targetHost, targetPort)))
        .flatMap(in.through)
    }
  }

}
