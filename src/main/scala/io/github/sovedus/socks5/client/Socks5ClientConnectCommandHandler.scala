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

import io.github.sovedus.socks5.client.auth.ClientAuthenticator
import io.github.sovedus.socks5.common.{AddressUtils, ReadWriter, Resolver}
import io.github.sovedus.socks5.common.Command.CONNECT
import io.github.sovedus.socks5.common.Socks5Exception.UnsupportedAddressTypeException

import cats.effect.Async
import cats.syntax.all.*

import com.comcast.ip4s.*
import fs2.Pipe

private[client] class Socks5ClientConnectCommandHandler[F[_]: Async](
    protected val rw: ReadWriter[F],
    protected val authenticators: Map[Byte, ClientAuthenticator[F]],
    protected val authMethods: Array[Byte],
    protected val resolver: Resolver[F],
    protected val resolveHostOnServer: Boolean
) extends Socks5ClientCommandHandler[F] {

  private val F: Async[F] = implicitly

  override protected def handleCommand(
      targetHost: Host,
      targetPort: Port
  ): F[Pipe[F, Byte, Byte]] = for {
    resolvedHost <- resolveHost(targetHost)
    _ <- sendCommandRequest(CONNECT, resolvedHost, targetPort)
    pipe <- parseCommandReply()
  } yield pipe

  private def parseCommandReply(): F[fs2.Pipe[F, Byte, Byte]] = for {
    (version, replyCode, _, addressType) <- rw.read4
    _ <- checkProtocolVersion(version)
    _ <- checkReplyCode(replyCode)
    _ <- addressType match {
      case 0x01 => AddressUtils.readIPv4(rw)
      case 0x04 => AddressUtils.readIPv6(rw)
      case aType => F.raiseError(UnsupportedAddressTypeException(aType))
    }
    _ <- AddressUtils.readPort(rw)
  } yield { (in: fs2.Stream[F, Byte]) => rw.reads.concurrently(in.through(rw.writes)) }

}

private[client] object Socks5ClientConnectCommandHandler {
  def apply[F[_]: Async](
      readWriter: ReadWriter[F],
      authenticators: Map[Byte, ClientAuthenticator[F]],
      resolver: Resolver[F],
      resolveHostOnServer: Boolean
  ): F[Socks5ClientConnectCommandHandler[F]] = Async[F]
    .delay(authenticators.view.keys.toArray)
    .map(authMethods =>
      new Socks5ClientConnectCommandHandler(
        readWriter,
        authenticators,
        authMethods,
        resolver,
        resolveHostOnServer
      )
    )
}
