package com.github.sovedus.socks5.common

sealed abstract class Socks5Exception(message: String = "")
    extends Exception(message)
    with Product

object Socks5Exception {

  case class ProtocolVersionException(version: Byte)
      extends Socks5Exception(s"Version ($version) not support")

  case class AuthenticationException(message: String) extends Socks5Exception(message)

  case class UnsupportedCommandException(command: Command)
      extends Socks5Exception(s"Unsupported command: $command")

  case class HandleCommandException(message: String) extends Socks5Exception(message)

  case class UnsupportedAddressTypeException(addressType: Byte)
      extends Socks5Exception(s"Unsupported address type: $addressType")

  case object NoSupportedAuthMethodException
      extends Socks5Exception("No supported authentication method")
}
