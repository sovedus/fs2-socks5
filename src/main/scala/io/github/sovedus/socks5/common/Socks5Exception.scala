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

import java.io.IOException

object Socks5Exception {

  final case class ProtocolVersionException(version: Byte)
      extends Exception(s"Version ($version) not support")

  final case class AuthenticationException(message: String) extends Exception(message)

  final case class UnsupportedCommandException(code: Byte)
      extends Exception(s"Unsupported command: 0x${code.toInt.toHexString}")

  final case class HandleCommandException(message: String) extends Exception(message)

  final case class UnsupportedAddressTypeException(addressType: Byte)
      extends Exception(s"Unsupported address type: $addressType")

  final case object NoSupportedAuthMethodException
      extends Exception("No supported authentication method")

  final case class ReachedEndOfStream()
      extends IOException("Reached end of stream while reading")

  final case class IncompleteReadException(expected: Int, actual: Int)
      extends IOException(
        s"Incomplete data read from stream: expected $expected bytes, got $actual bytes"
      )

  final case class IPv4ParseException(bytes: Array[Byte])
      extends IOException(
        s"Failed to parse IPv4 address from bytes: ${bytes.map(b => f"$b%02x").mkString("[", " ", "]")}"
      )

  final case class IPv6ParseException(bytes: Array[Byte])
      extends IOException(
        s"Failed to parse IPv6 address from bytes: ${bytes.map(b => f"$b%02x").mkString("[", " ", "]")}"
      )

}
