package com.github.sovedus.socks5.common.auth

sealed abstract class AuthenticationStatus(value: Byte) extends Product {
  def toByte: Byte = value
}

object AuthenticationStatus {
  case object SUCCESS extends AuthenticationStatus(0x00)
  case object FAILURE extends AuthenticationStatus(0x01)
}
