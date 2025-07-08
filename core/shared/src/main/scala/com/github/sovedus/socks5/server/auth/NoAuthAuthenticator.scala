package com.github.sovedus.socks5.server.auth

import cats.effect.Async
import com.github.sovedus.socks5.common.auth.AuthenticationStatus
import fs2.io.net.Socket

class NoAuthAuthenticator[F[_]: Async] extends ServerAuthenticator[F] {

  override def code: Byte = 0x00

  override def authenticate(socket: Socket[F]): F[AuthenticationStatus] =
    Async[F].pure(AuthenticationStatus.SUCCESS)
}

object NoAuthAuthenticator {
  def apply[F[_]: Async](): NoAuthAuthenticator[F] = new NoAuthAuthenticator()
}
