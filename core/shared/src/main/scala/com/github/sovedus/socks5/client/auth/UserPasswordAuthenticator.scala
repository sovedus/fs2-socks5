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

package com.github.sovedus.socks5.client.auth

import cats.effect.Async
import com.github.sovedus.socks5.client.auth.UserPasswordAuthenticator.AUTH_VERSION
import com.github.sovedus.socks5.common.Socks5Exception.AuthenticationException
import com.github.sovedus.socks5.common.auth.AuthenticationStatus
import fs2.Chunk
import fs2.io.net.Socket

import scala.collection.mutable.ArrayBuffer
import cats.syntax.all.*

class UserPasswordAuthenticator[F[_]: Async](authData: Chunk[Byte])
    extends ClientAuthenticator[F] {

  private val F: Async[F] = implicitly

  override def code: Byte = 0x02

  override def authenticate(socket: Socket[F]): F[AuthenticationStatus] = for {
    _ <- socket.write(authData)
    bytes <- socket.readN(2).map(c => (c(0), c(1)))
    (version, authStatus) = bytes
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

  def apply[F[_]: Async](user: String, password: String): F[UserPasswordAuthenticator[F]] =
    Async[F].defer {
      val userBytes = user.getBytes
      val passwordBytes = password.getBytes

      if (userBytes.length < 1) { throw new Exception("Username cannot be empty") }
      else if (userBytes.length > 255) { throw new Exception("Username is too long") }
      else if (passwordBytes.length < 1) { throw new Exception("Password cannot be empty") }
      else if (passwordBytes.length > 255) { throw new Exception("Password is too long") }

      makeAuthData(userBytes, passwordBytes).map(new UserPasswordAuthenticator(_))

    }

  private def makeAuthData[F[_]: Async](
      userBytes: Array[Byte],
      passwordBytes: Array[Byte]
  ): F[Chunk[Byte]] = Async[F].delay {
    val buf = new ArrayBuffer[Byte](3 + userBytes.length + passwordBytes.length)

    buf.addOne(AUTH_VERSION)
    buf.addOne(userBytes.length.toByte)
    buf.addAll(userBytes)
    buf.addOne(passwordBytes.length.toByte)
    buf.addAll(passwordBytes)

    Chunk.array(buf.toArray)
  }
}
