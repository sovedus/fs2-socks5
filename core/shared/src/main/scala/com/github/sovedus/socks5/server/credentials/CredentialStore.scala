package com.github.sovedus.socks5.server.credentials

trait CredentialStore[F[_], C] {
  def validate(credential: C): F[Boolean]
}
