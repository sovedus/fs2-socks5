/*
 * Copyright 2025 Sovedus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.sovedus.socks5.server.auth

import io.github.sovedus.socks5.common.ReadWriter
import io.github.sovedus.socks5.common.Socks5Exception.AuthenticationException
import io.github.sovedus.socks5.common.auth.{AuthenticationStatus, UserPasswordCredential}
import io.github.sovedus.socks5.server.credentials.CredentialStore

import cats.effect.Async
import cats.syntax.all.*

import java.nio.charset.StandardCharsets

import fs2.Chunk

final class UserPasswordAuthenticator[F[_]: Async](
    credentialStore: CredentialStore[F, UserPasswordCredential]
) extends ServerAuthenticator[F] {

  private val F: Async[F] = implicitly

  private val AUTH_VERSION: Byte = 0x01

  override def code: Byte = 0x02

  override def authenticate(rw: ReadWriter[F]): F[AuthenticationStatus] = for {
    (version, usernameLen) <- rw.read2
    _ <- F
      .raiseError(AuthenticationException(s"Unsupported auth version: $version"))
      .whenA(version != AUTH_VERSION)
    user <- rw.readN(usernameLen.toInt).map(c => new String(c.toArray, StandardCharsets.UTF_8))
    passLen <- rw.read1
    password <- rw.readN(passLen.toInt).map(c => new String(c.toArray, StandardCharsets.UTF_8))
    authStatus <- credentialStore
      .validate(UserPasswordCredential(user, password))
      .ifF(AuthenticationStatus.SUCCESS, AuthenticationStatus.FAILURE)
    _ <- rw.write(Chunk(AUTH_VERSION, authStatus.toByte))
  } yield authStatus

}

object UserPasswordAuthenticator {
  def apply[F[_]: Async](credentialStore: CredentialStore[F, UserPasswordCredential]) =
    new UserPasswordAuthenticator(credentialStore)
}
