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

package io.github.sovedus.socks5.server

import io.github.sovedus.socks5.common.{Command, Resolver}
import io.github.sovedus.socks5.server.auth.ServerAuthenticator

import cats.effect.{Async, Resource}
import cats.effect.syntax.all.*
import cats.syntax.all.*

import org.typelevel.log4cats.Logger

import com.comcast.ip4s.{Host, Port}
import fs2.io.net.Network

class Socks5Server[F[_]] private (
    val host: Host,
    val port: Port,
    val limitConnections: Int
)

object Socks5Server {

  private[server] def createAndStart[F[_]: Async: Network](
      host: Host,
      port: Port,
      authenticators: Map[Byte, ServerAuthenticator[F]],
      resolver: Resolver[F],
      limitConnections: Int,
      errorHandler: ErrorHandler[F],
      commands: Map[Command, Socks5ServerCommandHandler[F]],
      logger: Logger[F]
  ): Resource[F, Socks5Server[F]] =
    for {
      server <- Network[F].serverResource(host.some, port.some)
      (serverAddress, serverSockets) = server
      _ <- serverSockets
        .map { clientSocket =>
          fs2
            .Stream
            .eval(
              new Socks5ServerConnectionHandler(
                authenticators,
                commands,
                clientSocket,
                resolver).handle().handleErrorWith(errorHandler.handleException).voidError)
        }
        .parJoin(limitConnections)
        .compile
        .drain
        .background
    } yield new Socks5Server(
      serverAddress.host,
      serverAddress.port,
      limitConnections
    )

}
