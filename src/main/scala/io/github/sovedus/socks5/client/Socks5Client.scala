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

package io.github.sovedus.socks5.client

import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import _root_.io.github.sovedus.socks5.client.auth.ClientAuthenticator
import _root_.io.github.sovedus.socks5.common.Resolver
import com.comcast.ip4s.{Host, Port, SocketAddress}
import fs2.*
import fs2.io.net.{Network, Socket}
import org.typelevel.log4cats.Logger

trait Socks5Client[F[_]] {
  def connect(host: Host, port: Port): Pipe[F, Byte, Byte]

  def connectResource(host: Host, port: Port): Resource[F, Pipe[F, Byte, Byte]]

}

private[client] object Socks5Client {

  def create[F[_]: Async: Network](
      host: Host,
      port: Port,
      authenticators: Map[Byte, ClientAuthenticator[F]],
      resolver: Resolver[F],
      resolveHostOnServer: Boolean,
      logger: Logger[F]
  ): Socks5Client[F] = new Socks5Client[F] {
    override def connect(targetHost: Host, targetPort: Port): Pipe[F, Byte, Byte] = {
      (in: Stream[F, Byte]) =>
        Stream
          .resource(connectResource(targetHost, targetPort))
          .flatMap(pipe => in.through(pipe))
    }

    override def connectResource(
        targetHost: Host,
        targetPort: Port
    ): Resource[F, Pipe[F, Byte, Byte]] =
      createPipe(targetHost, targetPort) { socket =>
        Socks5ClientConnectCommandHandler(socket, authenticators, resolver, resolveHostOnServer)
      }

    private def createPipe[H <: Socks5ClientCommandHandler[F]](
        targetHost: Host,
        targetPort: Port
    )(cmdHandler: Socket[F] => F[H]): Resource[F, Pipe[F, Byte, Byte]] =
      Network[F].client(SocketAddress(host, port)).evalMap { socket =>
        cmdHandler(socket)
          .flatMap(_.handle(targetHost, targetPort))
          .map(pipe => (in: Stream[F, Byte]) => in.through(pipe))
      }

  }

}
