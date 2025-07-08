package com.github.sovedus.socks5.server.credentials

import cats.effect.Sync

final class StaticCredentialStore[F[_]: Sync](users: Map[String, String])
    extends CredentialStore[F, UserPasswordCredential] {

  private val F: Sync[F] = implicitly

  override def validate(credential: UserPasswordCredential): F[Boolean] =
    F.delay(users.get(credential.user).contains(credential.password))
}

object StaticCredentialStore {
  def apply[F[_]: Sync](users: Map[String, String]): StaticCredentialStore[F] =
    new StaticCredentialStore(users)
}
