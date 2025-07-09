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

package com.github.sovedus.socks5.client

import cats.effect.Async
import com.comcast.ip4s.*
import com.github.sovedus.socks5.client.auth.{ClientAuthenticator, NoAuthAuthenticator}
import com.github.sovedus.socks5.common.Resolver
import fs2.io.net.Network

final class Socks5ClientBuilder[F[_]: Async: Network] private (
    val host: Host,
    val port: Port,
    val resolveHostOnServer: Boolean,
    private val authenticators: Map[Byte, ClientAuthenticator[F]],
    private val resolver: Resolver[F]
) {
  private lazy val noAuthAuthenticator = NoAuthAuthenticator()

  def withHost(host: Host): Socks5ClientBuilder[F] = copy(host = host)

  def withPort(port: Port): Socks5ClientBuilder[F] = copy(port = port)

  def withResolveHostOnServer: Socks5ClientBuilder[F] = copy(resolveHostOnServer = true)

  def withAuthenticator(authenticator: ClientAuthenticator[F]): Socks5ClientBuilder[F] =
    copy(authenticators = authenticators.updated(authenticator.code, authenticator))

  def withResolver(resolver: Resolver[F]): Socks5ClientBuilder[F] = copy(resolver = resolver)

  def build: Socks5Client[F] = {
    val nonEmptyAuthenticators =
      if (authenticators.isEmpty) Map(noAuthAuthenticator.code -> noAuthAuthenticator)
      else authenticators

    Socks5Client.create(host, port, nonEmptyAuthenticators, resolver, resolveHostOnServer)
  }

  private def copy(
      host: Host = this.host,
      port: Port = this.port,
      resolveHostOnServer: Boolean = this.resolveHostOnServer,
      authenticators: Map[Byte, ClientAuthenticator[F]] = this.authenticators,
      resolver: Resolver[F] = this.resolver
  ): Socks5ClientBuilder[F] = new Socks5ClientBuilder(
    host = host,
    port = port,
    resolveHostOnServer = resolveHostOnServer,
    authenticators = authenticators,
    resolver = resolver)
}

object Socks5ClientBuilder {

  def default[F[_]: Async: Network] = new Socks5ClientBuilder(
    host = host"localhost",
    port = port"1080",
    resolveHostOnServer = true,
    authenticators = Map.empty,
    resolver = Resolver.default)
}
