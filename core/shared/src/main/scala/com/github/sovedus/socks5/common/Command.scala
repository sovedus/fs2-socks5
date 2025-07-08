package com.github.sovedus.socks5.common

import com.github.sovedus.socks5.common.Socks5Exception.HandleCommandException

sealed trait Command extends Serializable with Product {
  def code: Byte

  override def toString: String = this match {
    case Command.CONNECT => "CONNECT"
    case Command.BIND => "BIND"
    case Command.UDP_ASSOCIATE => "UDP_ASSOCIATE"
  }
}

object Command {
  case object CONNECT extends Command {
    override def code: Byte = 0x01
  }

  case object BIND extends Command {
    override def code: Byte = 0x02
  }

  case object UDP_ASSOCIATE extends Command {
    override def code: Byte = 0x03
  }

  def from(cmd: Byte): Command = cmd match {
    case 0x01 => CONNECT
    case 0x02 => BIND
    case 0x03 => UDP_ASSOCIATE
    case c => throw HandleCommandException(s"Invalid command: $c")
  }
}
