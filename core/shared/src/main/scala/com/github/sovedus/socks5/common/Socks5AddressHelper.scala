package com.github.sovedus.socks5.common

import cats.effect.Sync
import cats.syntax.all._
import com.comcast.ip4s._
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
    .map(chunk => Ipv4Address.fromBytes(chunk(0), chunk(1), chunk(2), chunk(3)))

  private def parseIPv6(): F[IpAddress] = socket.readN(IPv6_LEN).map { chunk =>
    Ipv6Address.fromBytes(
      chunk(0),
      chunk(1),
      chunk(2),
      chunk(3),
      chunk(4),
      chunk(5),
      chunk(6),
      chunk(7),
      chunk(8),
      chunk(9),
      chunk(10),
      chunk(11),
      chunk(12),
      chunk(13),
      chunk(14),
      chunk(15)
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
