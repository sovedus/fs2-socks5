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
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port, SocketAddress}
import com.github.sovedus.socks5.client.auth.ClientAuthenticator
import com.github.sovedus.socks5.common.Resolver
import fs2.Pipe
import fs2.io.net.{Network, Socket}

trait Socks5Client[F[_]] {
  def connect(host: Host, port: Port): fs2.Pipe[F, Byte, Byte]
}

private[client] object Socks5Client {

  def create[F[_]: Async: Network](
      host: Host,
      port: Port,
      authenticators: Map[Byte, ClientAuthenticator[F]],
      resolver: Resolver[F],
      resolveHostOnServer: Boolean
  ): Socks5Client[F] = new Socks5Client[F] {
    override def connect(targetHost: Host, targetPort: Port): Pipe[F, Byte, Byte] =
      createPipe(targetHost, targetPort) { socket =>
        Socks5ClientConnectCommandHandler(socket, authenticators, resolver, resolveHostOnServer)
      }

    private def createPipe[H <: Socks5ClientCommandHandler[F]](
        targetHost: Host,
        targetPort: Port
    )(cmdHandler: Socket[F] => F[H]): Pipe[F, Byte, Byte] = { in =>
      fs2
        .Stream
        .resource(Network[F].client(SocketAddress(host, port)))
        .evalMap(socket => cmdHandler(socket).flatMap(_.handle(targetHost, targetPort)))
        .flatMap(in.through)
    }
  }

}
