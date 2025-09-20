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

import cats.effect.Sync
import cats.syntax.all.*

import java.nio.charset.StandardCharsets

import com.comcast.ip4s.*

import Socks5Constants.{IPv4_LEN, IPv6_LEN}
import Socks5Exception.{HandleCommandException, IPv4ParseException, IPv6ParseException}

object AddressUtils {

  def getAddressType(host: Host): Byte = host match {
    case _: Ipv4Address => 0x01
    case _: Hostname => 0x03
    case _: IDN => 0x03
    case _: Ipv6Address => 0x04
  }

  def readPort[F[_]: Sync](reader: Reader[F]): F[Port] = reader.read2
    .map(t => BigInt(Array(t._1, t._2)).toInt)
    .map(Port.fromInt)
    .map(_.getOrElse(throw HandleCommandException("Failed to parse port")))

  def readIPv4[F[_]: Sync](reader: Reader[F]): F[IpAddress] = {
    for {
      chunk <- reader.readN(IPv4_LEN)
      array = chunk.toArray
      address <- Ipv4Address.fromBytes(chunk.toArray).toRight(IPv4ParseException(array)).liftTo
    } yield address
  }

  def readIPv6[F[_]: Sync](reader: Reader[F]): F[IpAddress] = {
    for {
      chunk <- reader.readN(IPv6_LEN)
      array = chunk.toArray
      address <- Ipv6Address.fromBytes(chunk.toArray).toRight(IPv6ParseException(array)).liftTo
    } yield address
  }

  def readDomain[F[_]: Sync](reader: Reader[F]): F[Hostname] = {
    for {
      count <- reader.read1.map(_.toInt)
      _ = if (count <= 0)
        throw new IllegalArgumentException(s"Invalid FQDN length: $count (must be positive)")
      chunkData <- reader.readN(count)
      str = new String(chunkData.toArray, StandardCharsets.UTF_8)
      hostname = Hostname
        .fromString(str)
        .getOrElse(throw HandleCommandException("Failed to parse domain name"))
    } yield hostname
  }
}
