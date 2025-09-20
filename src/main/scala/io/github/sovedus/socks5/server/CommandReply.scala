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

package io.github.sovedus.socks5.server

import io.github.sovedus.socks5.common.{CommandReplyType, Writer}
import io.github.sovedus.socks5.common.Socks5Constants.VERSION_SOCKS5

import cats.effect.Sync

import scala.collection.mutable.ArrayBuffer

import com.comcast.ip4s.{IpAddress, Port}
import com.comcast.ip4s.IpVersion.V4
import fs2.Chunk

case class CommandReply(replyType: CommandReplyType, ipAddress: IpAddress, port: Port) {
  def send[F[_]: Sync](writer: Writer[F]): F[Unit] = Sync[F].defer {
    val addressBytes = ipAddress.toBytes
    val portBytes = Array[Byte]((port.value >> 8).toByte, port.value.toByte)

    val length = 6 + addressBytes.length

    val addressType: Byte = if (ipAddress.version == V4) 0x01 else 0x04

    val buf = new ArrayBuffer[Byte](length)
    buf.addOne(VERSION_SOCKS5)
    buf.addOne(replyType.code)
    buf.addOne(0x00.toByte)
    buf.addOne(addressType)
    buf.addAll(addressBytes)
    buf.addAll(portBytes)

    writer.write(Chunk.array(buf.toArray))
  }
}
