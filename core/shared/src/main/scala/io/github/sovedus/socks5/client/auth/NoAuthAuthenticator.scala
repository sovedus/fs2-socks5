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

import cats.effect.Async
import fs2.io.net.Socket
import io.github.sovedus.socks5.common.auth.AuthenticationStatus

class NoAuthAuthenticator[F[_]: Async] extends ClientAuthenticator[F] {

  override def code: Byte = 0x00

  override def authenticate(socket: Socket[F]): F[AuthenticationStatus] =
    Async[F].pure(AuthenticationStatus.SUCCESS)
}

object NoAuthAuthenticator {
  def apply[F[_]: Async](): NoAuthAuthenticator[F] = new NoAuthAuthenticator()
}
