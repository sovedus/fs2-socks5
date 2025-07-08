package com.github.sovedus.socks5.server

import cats.effect.Sync
import com.comcast.ip4s.IpVersion.V4
import com.comcast.ip4s.{IpAddress, Port}
import com.github.sovedus.socks5.common.CommandReplyType
import com.github.sovedus.socks5.common.Socks5Constants.VERSION_SOCKS5_BYTE
import fs2.Chunk
import fs2.io.net.Socket

import scala.collection.mutable.ArrayBuffer

case class CommandReply(replyType: CommandReplyType, ipAddress: IpAddress, port: Port) {
  def send[F[_]: Sync](socket: Socket[F]): F[Unit] = Sync[F].defer {
    val addressBytes = ipAddress.toBytes
    val portBytes = Array[Byte]((port.value >> 8).toByte, port.value.toByte)

    val length = 6 + addressBytes.length

    val addressType: Byte = if (ipAddress.version == V4) 0x01 else 0x04

    val buf = new ArrayBuffer[Byte](length)
    buf.addOne(VERSION_SOCKS5_BYTE)
    buf.addOne(replyType.code)
    buf.addOne(0x00.toByte)
    buf.addOne(addressType)
    buf.addAll(addressBytes)
    buf.addAll(portBytes)

    socket.write(Chunk.array(buf.toArray))
  }
}
