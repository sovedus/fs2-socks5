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

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommandSpec extends AnyFlatSpec with Matchers with Inspectors {

  "Command" should "correctly convert command codes to string representations including unknown values" in
    List(
      (Command.CONNECT, "CONNECT"),
      (Command.BIND, "BIND"),
      (Command.UDP_ASSOCIATE, "UDP_ASSOCIATE"),
      (new Command(0x06), "OTHER(0x06)")
    ).forall { case (cmd, cmdStr) => cmd.toString == cmdStr }
}
