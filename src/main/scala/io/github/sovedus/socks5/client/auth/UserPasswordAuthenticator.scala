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

package io.github.sovedus.socks5.client.auth

import io.github.sovedus.socks5.client.auth.UserPasswordAuthenticator.AUTH_VERSION
import io.github.sovedus.socks5.common.ReadWriter
import io.github.sovedus.socks5.common.Socks5Exception.AuthenticationException
import io.github.sovedus.socks5.common.auth.{AuthenticationStatus, UserPasswordCredential}

import cats.effect.{Async, Sync}
import cats.syntax.all.*

import scala.collection.mutable.ArrayBuffer

import fs2.Chunk

class UserPasswordAuthenticator[F[_]](authData: Chunk[Byte])(implicit F: Sync[F])
    extends ClientAuthenticator[F] {

  override def code: Byte = 0x02

  override def authenticate(rw: ReadWriter[F]): F[AuthenticationStatus] = for {
    _ <- rw.write(authData)
    (version, authStatus) <- rw.read2
    _ <- F
      .raiseError(AuthenticationException(s"Unsupported auth version: $version"))
      .whenA(version != AUTH_VERSION)

  } yield authStatus match {
    case 0x00 => AuthenticationStatus.SUCCESS
    case _ => AuthenticationStatus.FAILURE
  }
}

object UserPasswordAuthenticator {
  private val AUTH_VERSION: Byte = 0x01

  def apply[F[_]: Async](credential: UserPasswordCredential): UserPasswordAuthenticator[F] = {
    new UserPasswordAuthenticator(makeAuthData(credential))
  }

  private def makeAuthData(
      credential: UserPasswordCredential
  ): Chunk[Byte] = {
    val userBytes = credential.user.getBytes
    val passwordBytes = credential.password.getBytes
    val buf = new ArrayBuffer[Byte](3 + userBytes.length + passwordBytes.length)

    buf.addOne(AUTH_VERSION)
    buf.addOne(userBytes.length.toByte)
    buf.addAll(userBytes)
    buf.addOne(passwordBytes.length.toByte)
    buf.addAll(passwordBytes)

    Chunk.array(buf.toArray)
  }
}
