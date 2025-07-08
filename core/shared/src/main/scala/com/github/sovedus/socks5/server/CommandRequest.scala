package com.github.sovedus.socks5.server

import com.comcast.ip4s.{IpAddress, Port}
import com.github.sovedus.socks5.common.Command

case class CommandRequest(command: Command, address: IpAddress, port: Port)
