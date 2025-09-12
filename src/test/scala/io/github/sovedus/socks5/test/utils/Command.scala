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

package io.github.sovedus.socks5.test.utils

import io.github.sovedus.socks5.common.CommandReplyType

import com.comcast.ip4s.{Ipv4Address, Ipv6Address}

object Command {
  val protocolVersion: Byte = 0x5
  val reserved: Byte = 0x00

  val ipv4AddressType: Byte = 0x01
  val ipv6AddressType: Byte = 0x04
  val domainAddressType: Byte = 0x03

  object Connect {
    val commandCode: Byte = 0x01

    object Request {
      def fromDomain(domain: String, port: Int): List[Byte] = {
        val domainBytes: List[Byte] = domain.getBytes.toList
        val domainLength: Byte = domainBytes.length.toByte
        val portBytes = Array[Byte]((port >> 8).toByte, port.toByte).toList
        List[Byte](
          protocolVersion,
          commandCode,
          reserved,
          domainAddressType,
          domainLength) ++ domainBytes ++ portBytes
      }

      def fromIpV4(ip: Ipv4Address, port: Int): List[Byte] = {
        val portBytes = Array[Byte]((port >> 8).toByte, port.toByte).toList
        List[Byte](
          protocolVersion,
          commandCode,
          reserved,
          ipv4AddressType) ++ ip.toBytes ++ portBytes
      }

      def fromIpV6(ip: Ipv6Address, port: Int): List[Byte] = {
        val portBytes = Array[Byte]((port >> 8).toByte, port.toByte).toList
        List[Byte](
          protocolVersion,
          commandCode,
          reserved,
          ipv6AddressType) ++ ip.toBytes ++ portBytes
      }
    }

    object Response {

      def fromDomain(replyType: CommandReplyType, domain: String, port: Int): List[Byte] = {
        val domainBytes: List[Byte] = domain.getBytes.toList
        val domainLength: Byte = domainBytes.length.toByte
        val portBytes = Array[Byte]((port >> 8).toByte, port.toByte).toList

        List[Byte](
          protocolVersion,
          replyType.code,
          reserved,
          domainAddressType,
          domainLength) ++ domainBytes ++ portBytes
      }

      def fromIpV4(replyType: CommandReplyType, ip: Ipv4Address, port: Int): List[Byte] = {
        val portBytes = Array[Byte]((port >> 8).toByte, port.toByte).toList

        List[Byte](
          protocolVersion,
          replyType.code,
          reserved,
          ipv4AddressType) ++ ip.toBytes ++ portBytes
      }

      def fromIpV6(replyType: CommandReplyType, ip: Ipv6Address, port: Int): List[Byte] = {
        val portBytes = Array[Byte]((port >> 8).toByte, port.toByte).toList

        List[Byte](
          protocolVersion,
          replyType.code,
          reserved,
          ipv6AddressType) ++ ip.toBytes ++ portBytes
      }

    }
  }
}
