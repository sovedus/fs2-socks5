package com.github.sovedus.socks5.server

import cats.effect.Async
import com.comcast.ip4s.{IpAddress, Port, SocketAddress}
import fs2.io.net.Network

private[server] class Socks5ServerConnectCommandHandler[F[_]: Async: Network]
    extends Socks5ServerCommandHandler[F] {

  override def handle(ipAddress: IpAddress, port: Port)(
      onConnectionSuccess: F[Unit]
  ): fs2.Pipe[F, Byte, Byte] = { in =>
    fs2
      .Stream
      .resource(Network[F].client(SocketAddress(ipAddress, port)))
      .evalTap(_ => onConnectionSuccess)
      .flatMap(dstSocket => dstSocket.reads.concurrently(in.through(dstSocket.writes)))
  }

}
