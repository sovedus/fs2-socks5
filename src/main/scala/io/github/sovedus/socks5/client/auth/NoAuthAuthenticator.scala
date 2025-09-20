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

import io.github.sovedus.socks5.common.ReadWriter
import io.github.sovedus.socks5.common.auth.AuthenticationStatus

import cats.effect.Sync

class NoAuthAuthenticator[F[_]](implicit F: Sync[F]) extends ClientAuthenticator[F] {

  override def code: Byte = 0x00

  override def authenticate(rw: ReadWriter[F]): F[AuthenticationStatus] =
    F.pure(AuthenticationStatus.SUCCESS)
}

object NoAuthAuthenticator {
  def apply[F[_]: Sync](): NoAuthAuthenticator[F] = new NoAuthAuthenticator()
}
