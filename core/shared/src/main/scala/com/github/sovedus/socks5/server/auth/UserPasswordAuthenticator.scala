package com.github.sovedus.socks5.server.auth

import cats.effect.Async
import cats.syntax.all._
import com.github.sovedus.socks5.common.Socks5Exception.AuthenticationException
import com.github.sovedus.socks5.common.auth.AuthenticationStatus
import com.github.sovedus.socks5.server.credentials.{CredentialStore, UserPasswordCredential}
import fs2.Chunk
import fs2.io.net.Socket

import java.nio.charset.StandardCharsets

final class UserPasswordAuthenticator[F[_]: Async](
    credentialStore: CredentialStore[F, UserPasswordCredential]
) extends ServerAuthenticator[F] {

  private val F: Async[F] = implicitly

  private val AUTH_VERSION: Byte = 0x01

  override def code: Byte = 0x02

  override def authenticate(socket: Socket[F]): F[AuthenticationStatus] = for {
    bytes <- socket.readN(2).map(c => (c(0), c(1)))
    (version, usernameLen) = bytes
    _ <- F
      .raiseError(AuthenticationException(s"Unsupported auth version: $version"))
      .whenA(version != AUTH_VERSION)
    user <- socket.readN(usernameLen).map(c => new String(c.toArray, StandardCharsets.UTF_8))
    passLen <- socket.readN(1).map(_(0))
    password <- socket.readN(passLen).map(c => new String(c.toArray, StandardCharsets.UTF_8))
    authStatus <- credentialStore
      .validate(UserPasswordCredential(user, password))
      .ifF(AuthenticationStatus.SUCCESS, AuthenticationStatus.FAILURE)
    _ <- socket.write(Chunk(AUTH_VERSION, authStatus.toByte))
  } yield authStatus

}

object UserPasswordAuthenticator {
  def apply[F[_]: Async](credentialStore: CredentialStore[F, UserPasswordCredential]) =
    new UserPasswordAuthenticator(credentialStore)
}
