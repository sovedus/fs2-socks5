package com.github.sovedus.socks5.common

sealed abstract class CommandReplyType(value: Byte, description: String)
    extends Serializable
    with Product {
  def code: Byte = value

  override def toString: String = description
}

object CommandReplyType {
  case object SUCCEEDED extends CommandReplyType(0x00, "Succeeded")
  case object GENERAL_SOCKS_SERVER_FAILURE
      extends CommandReplyType(0x01, "General SOCKS server failure")
  case object CONNECTION_NOT_ALLOWED
      extends CommandReplyType(0x02, "Connection not allowed by ruleset")
  case object NETWORK_UNREACHABLE extends CommandReplyType(0x03, "Network unreachable")
  case object HOST_UNREACHABLE extends CommandReplyType(0x04, "Host unreachable")
  case object CONNECTION_REFUSED extends CommandReplyType(0x05, "Connection refused")
  case object TTL_EXPIRED extends CommandReplyType(0x06, "TTL expired")
  case object COMMAND_NOT_SUPPORTED extends CommandReplyType(0x07, "Command not supported")
  case object ADDRESS_TYPE_NOT_SUPPORTED
      extends CommandReplyType(0x08, "Address type not supported")
  case class UNASSIGNED(override val code: Byte) extends CommandReplyType(code, "Unassigned")

  def from(code: Byte): CommandReplyType = code match {
    case 0x00 => SUCCEEDED
    case 0x01 => GENERAL_SOCKS_SERVER_FAILURE
    case 0x02 => CONNECTION_NOT_ALLOWED
    case 0x03 => NETWORK_UNREACHABLE
    case 0x04 => HOST_UNREACHABLE
    case 0x05 => CONNECTION_REFUSED
    case 0x06 => TTL_EXPIRED
    case 0x07 => COMMAND_NOT_SUPPORTED
    case 0x08 => ADDRESS_TYPE_NOT_SUPPORTED
    case c => UNASSIGNED(c)
  }
}
