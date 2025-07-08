package com.github.sovedus.socks5.common

import com.comcast.ip4s.{Ipv4Address, Port}

object Socks5Constants {
  val IPv4_LEN: Int = 4
  val IPv6_LEN: Int = 16

  val IPv4_ZERO: Ipv4Address = Ipv4Address.fromBytes(0, 0, 0, 0)
  val PORT_ZERO: Port = Port.fromInt(Port.MinValue).get

  val VERSION_SOCKS5: Int = 0x05
  val VERSION_SOCKS5_BYTE: Byte = 0x05

  val NO_ACCEPTABLE_METHODS: Byte = 0xff.toByte
}
