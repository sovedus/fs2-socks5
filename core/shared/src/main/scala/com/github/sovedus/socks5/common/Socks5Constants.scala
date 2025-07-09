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
