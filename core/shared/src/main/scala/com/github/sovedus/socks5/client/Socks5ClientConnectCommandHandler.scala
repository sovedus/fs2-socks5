package com.github.sovedus.socks5.client

import cats.effect.Async
import cats.syntax.all._
import com.comcast.ip4s._
import com.github.sovedus.socks5.client.auth.ClientAuthenticator
import com.github.sovedus.socks5.common.Command.CONNECT
import com.github.sovedus.socks5.common.Resolver
import fs2.Pipe
import fs2.io.net.Socket

private[client] class Socks5ClientConnectCommandHandler[F[_]: Async](
    val socket: Socket[F],
    val authenticators: Map[Byte, ClientAuthenticator[F]],
    val authMethods: Array[Byte],
    val resolver: Resolver[F],
    val resolveHostOnServer: Boolean
) extends Socks5ClientCommandHandler[F] {

  override protected val F: Async[F] = implicitly

  override protected def handleCommand(
      targetHost: Host,
      targetPort: Port
  ): F[Pipe[F, Byte, Byte]] = for {
    resolvedHost <- resolveHost(targetHost)
    _ <- sendCommandRequest(CONNECT, resolvedHost, targetPort)
    pipe <- parseCommandReply()
  } yield pipe

  private def parseCommandReply(): F[fs2.Pipe[F, Byte, Byte]] = for {
    bytes <- socket.readN(4).map(c => (c(0), c(1), c(2), c(3)))
    (version, replyCode, _, addressType) = bytes
    _ <- checkProtocolVersion(version)
    _ <- checkReplyCode(replyCode)
    _ <- parseAddress(addressType)
    _ <- parsePort()
  } yield { (in: fs2.Stream[F, Byte]) => socket.reads.concurrently(in.through(socket.writes)) }

}

private[client] object Socks5ClientConnectCommandHandler {
  def apply[F[_]: Async](
      socket: Socket[F],
      authenticators: Map[Byte, ClientAuthenticator[F]],
      resolver: Resolver[F],
      resolveHostOnServer: Boolean
  ): F[Socks5ClientConnectCommandHandler[F]] = Async[F]
    .delay(authenticators.view.keys.toArray)
    .map(authMethods =>
      new Socks5ClientConnectCommandHandler(
        socket,
        authenticators,
        authMethods,
        resolver,
        resolveHostOnServer))
}
