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

import Socks5Exception.HandleCommandException

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
