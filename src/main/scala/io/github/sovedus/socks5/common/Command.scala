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

import io.github.sovedus.socks5.common.Socks5Exception.UnsupportedCommandException

final class Command(val code: Byte) extends AnyVal {

  override def toString: String = code match {
    case 0x01 => "CONNECT"
    case 0x02 => "BIND"
    case 0x03 => "UDP_ASSOCIATE"
    case _ => s"OTHER($code)"
  }
}

object Command {
  val CONNECT: Command = Command(0x01)
  val BIND: Command = Command(0x02)
  val UDP_ASSOCIATE: Command = Command(0x03)

  def apply(cmd: Byte): Command = cmd match {
    case 0x01 | 0x02 | 0x03 => new Command(cmd)
    case c => throw UnsupportedCommandException(c)
  }
}
