package com.github.sovedus.socks5.server

import com.comcast.ip4s.{IpAddress, Port}

private[server] trait Socks5ServerCommandHandler[F[_]] {

  /** @param ipAddress
    *   received from client
    * @param port
    *   received from client
    * @param onConnectionSuccess
    *   must be call after success connection
    * @return
    *   pipe with input data from client and output data from destination
    */
  def handle(ipAddress: IpAddress, port: Port)(
      onConnectionSuccess: F[Unit]
  ): fs2.Pipe[F, Byte, Byte]
}
