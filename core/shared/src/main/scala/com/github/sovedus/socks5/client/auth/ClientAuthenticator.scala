package com.github.sovedus.socks5.client.auth

import com.github.sovedus.socks5.common.auth.AuthenticationStatus
import fs2.io.net.Socket

trait ClientAuthenticator[F[_]] {
  def code: Byte
  def authenticate(socket: Socket[F]): F[AuthenticationStatus]
}
