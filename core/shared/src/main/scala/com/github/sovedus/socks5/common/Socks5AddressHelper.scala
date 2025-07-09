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

package com.github.sovedus.socks5.common

import cats.effect.Sync
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.github.sovedus.socks5.common.Socks5Constants.{IPv4_LEN, IPv6_LEN}
import com.github.sovedus.socks5.common.Socks5Exception.{
  HandleCommandException,
  UnsupportedAddressTypeException
}
import fs2.io.net.Socket

import java.nio.charset.StandardCharsets

abstract class Socks5AddressHelper[F[_]: Sync] {
  protected val F: Sync[F]
  protected val socket: Socket[F]
  protected val resolver: Resolver[F]

  protected def parseAddress(addressType: Byte): F[IpAddress] = F.defer {
    addressType match {
      case 0x01 => parseIPv4()
      case 0x03 => parseFQDN().flatMap(resolver.resolve)
      case 0x04 => parseIPv6()
      case aType => F.raiseError(UnsupportedAddressTypeException(aType))
    }
  }

  protected def getAddressType(host: Host): Byte = host match {
    case _: Ipv4Address => 0x01
    case _: Hostname => 0x03
    case _: IDN => 0x03
    case _: Ipv6Address => 0x04
  }

  protected def parsePort(): F[Port] = socket
    .readN(2)
    .map(chunk => BigInt(chunk.toArray).toInt)
    .map(Port.fromInt)
    .map(_.getOrElse(throw HandleCommandException("Failed to parse port")))

  private def parseIPv4(): F[IpAddress] = socket
    .readN(IPv4_LEN)
    .map(chunk => Ipv4Address.fromBytes(chunk(0).toInt, chunk(1).toInt, chunk(2).toInt, chunk(3).toInt))

  private def parseIPv6(): F[IpAddress] = socket.readN(IPv6_LEN).map { chunk =>
    Ipv6Address.fromBytes(
      chunk(0).toInt,
      chunk(1).toInt,
      chunk(2).toInt,
      chunk(3).toInt,
      chunk(4).toInt,
      chunk(5).toInt,
      chunk(6).toInt,
      chunk(7).toInt,
      chunk(8).toInt,
      chunk(9).toInt,
      chunk(10).toInt,
      chunk(11).toInt,
      chunk(12).toInt,
      chunk(13).toInt,
      chunk(14).toInt,
      chunk(15).toInt
    )
  }

  private def parseFQDN(): F[Hostname] = socket
    .readN(1)
    .map(_(0).toInt)
    .flatMap(socket.readN)
    .map(chunk => new String(chunk.toArray, StandardCharsets.UTF_8))
    .map(Hostname.fromString)
    .map(_.getOrElse(throw HandleCommandException("Failed to parse domain name")))
}
