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

package io.github.sovedus.socks5.common

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all.*

import java.io.EOFException
import java.nio.charset.StandardCharsets

import com.comcast.ip4s.*
import fs2.io.net.Socket

import Socks5Constants.{IPv4_LEN, IPv6_LEN}
import Socks5Exception.{HandleCommandException, UnsupportedAddressTypeException}

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

  private def parseIPv4(): F[IpAddress] = OptionT(socket.read(IPv4_LEN))
    .getOrRaise(new EOFException(
      "Failed to read IPv4 address: connection closed before receiving 4 bytes"))
    .flatTap(c =>
      F.raiseWhen(c.size != IPv4_LEN)(
        new EOFException(s"Incomplete IPv4 address: expected $IPv4_LEN bytes, " +
          s"but received only ${c.size} bytes before connection closed")))
    .map(chunk =>
      Ipv4Address.fromBytes(chunk(0).toInt, chunk(1).toInt, chunk(2).toInt, chunk(3).toInt))

  private def parseIPv6(): F[IpAddress] = OptionT(socket.read(IPv6_LEN))
    .getOrRaise(new EOFException(
      "Failed to read IPv6 address: connection closed before receiving any bytes"))
    .flatTap(c =>
      F.raiseWhen(c.size != IPv6_LEN)(
        new EOFException(s"Incomplete IPv6 address: expected $IPv6_LEN bytes (16 octets), " +
          s"but received only ${c.size} bytes before stream ended")))
    .map { chunk =>
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

  private def parseFQDN(): F[Hostname] = {
    val optF = for {
      chunkSize <- OptionT(socket.read(1))
      count = chunkSize(0).toInt
      _ = if (count <= 0)
        throw new IllegalArgumentException(s"Invalid FQDN length: $count (must be positive)")
      chunkData <- OptionT(socket.read(count))
      _ = if (chunkData.size != count)
        throw new EOFException(
          s"Expected $count bytes for FQDN, but got only ${chunkData.size}")
      str = new String(chunkData.toArray, StandardCharsets.UTF_8)
      hostname = Hostname
        .fromString(str)
        .getOrElse(throw HandleCommandException("Failed to parse domain name"))
    } yield hostname

    optF.getOrRaise(new EOFException("Failed to read FQDN data byte(s)"))
  }
}
