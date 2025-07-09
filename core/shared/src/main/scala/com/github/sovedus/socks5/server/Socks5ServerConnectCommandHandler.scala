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

package com.github.sovedus.socks5.server

import cats.effect.Async
import com.comcast.ip4s.{IpAddress, Port, SocketAddress}
import fs2.io.net.Network

private[server] class Socks5ServerConnectCommandHandler[F[_]: Async: Network]
    extends Socks5ServerCommandHandler[F] {

  override def handle(ipAddress: IpAddress, port: Port)(
      onConnectionSuccess: F[Unit]
  ): fs2.Pipe[F, Byte, Byte] = { in =>
    fs2
      .Stream
      .resource(Network[F].client(SocketAddress(ipAddress, port)))
      .evalTap(_ => onConnectionSuccess)
      .flatMap(dstSocket => dstSocket.reads.concurrently(in.through(dstSocket.writes)))
  }

}
