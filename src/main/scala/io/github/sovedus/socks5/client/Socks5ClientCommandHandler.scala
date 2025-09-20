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
import io.github.sovedus.socks5.common.*
import io.github.sovedus.socks5.common.Socks5Constants.VERSION_SOCKS5
import io.github.sovedus.socks5.common.Socks5Exception.*
import io.github.sovedus.socks5.common.auth.AuthenticationStatus

import cats.effect.Sync
import cats.syntax.all.*

import scala.collection.mutable.ArrayBuffer

import com.comcast.ip4s.*
import fs2.Chunk

private[client] abstract class Socks5ClientCommandHandler[F[_]: Sync] {
  private type AuthMethod = Byte

  private val F: Sync[F] = implicitly

  protected val rw: ReadWriter[F]
  protected val authenticators: Map[AuthMethod, ClientAuthenticator[F]]
  protected val authMethods: Array[AuthMethod]
  protected val resolver: Resolver[F]
  protected val resolveHostOnServer: Boolean

  protected def handleCommand(targetHost: Host, targetPort: Port): F[fs2.Pipe[F, Byte, Byte]]

  final def handle(targetHost: Host, targetPort: Port): F[fs2.Pipe[F, Byte, Byte]] = for {
    authMethod <- handleHandshake()
    _ <- handleAuthentication(authMethod)
    pipe <- handleCommand(targetHost, targetPort)
  } yield pipe

  private def handleHandshake(): F[Byte] = for {
    _ <- sendHandshakeRequest()
    (version, authMethod) <- rw.read2
    _ <- checkProtocolVersion(version)
  } yield authMethod

  private def handleAuthentication(authMethod: Byte): F[Unit] = Sync[F].defer {
    authenticators
      .get(authMethod)
      .toOptionT
      .semiflatMap(_.authenticate(rw))
      .getOrRaise(NoSupportedAuthMethodException)
      .map(_ == AuthenticationStatus.SUCCESS)
      .ifM(F.unit, F.raiseError(AuthenticationException("User authentication failed")))
  }

  private def sendHandshakeRequest(): F[Unit] = F.defer {
    val buf = new ArrayBuffer[Byte](2 + authMethods.length)

    buf.addOne(VERSION_SOCKS5)
    buf.addOne(authMethods.length.toByte)
    buf.addAll(authMethods)

    rw.write(Chunk.array(buf.toArray))
  }

  protected def sendCommandRequest(command: Command, host: Host, port: Port): F[Unit] =
    F.defer {
      val (variableAddress, addressSize, addressBytes) = host match {
        case address: Ipv4Address => (false, 4, address.toBytes)
        case hostname: Hostname =>
          val b = hostname.toString.getBytes
          (true, b.length, b)
        case idn: IDN =>
          val b = idn.hostname.toString.getBytes
          (true, b.length, b)
        case address: Ipv6Address => (false, 16, address.toBytes)
      }

      val sizeAddressByte = if (variableAddress) 1 else 0
      val portBytes = Array[Byte]((port.value >> 8).toByte, port.value.toByte)

      val buf = new ArrayBuffer[Byte](6 + sizeAddressByte + addressSize)

      buf.addOne(VERSION_SOCKS5)
      buf.addOne(command.code)
      buf.addOne(0x00)
      buf.addOne(AddressUtils.getAddressType(host))

      if (variableAddress) { buf.addOne(addressSize.toByte) }

      buf.addAll(addressBytes)
      buf.addAll(portBytes)

      rw.write(Chunk.array(buf.toArray))
    }

  protected def checkProtocolVersion(version: Byte): F[Unit] =
    F.raiseError(ProtocolVersionException(version)).whenA(version != VERSION_SOCKS5)

  protected def checkReplyCode(replyCode: Byte): F[Unit] =
    CommandReplyType.from(replyCode) match {
      case CommandReplyType.SUCCEEDED => F.unit
      case ct => F.raiseError[Unit](HandleCommandException(ct.toString))
    }

  protected def resolveHost(host: Host): F[Host] = {
    val resolveHostname: F[IpAddress] = host match {
      case address: IpAddress => address.pure
      case hostname: Hostname => resolver.resolve(hostname)
      case idn: IDN => resolver.resolve(idn.hostname)
    }

    resolveHostOnServer.pure.ifM(host.pure, resolveHostname.map(h => h: Host))
  }
}
