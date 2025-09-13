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

package io.github.sovedus.socks5.test.utils

import cats.data.NonEmptyList

object Handshake {
  val protocolVersion: Byte = 0x05

  val noAuthenticationMethod: Byte = 0x00
  val userPasswordAuthenticationMethod: Byte = 0x02
  val noAcceptableAuthenticationMethods: Byte = 0xff.toByte

  object Request {
    def withoutAuthentication: List[Byte] =
      create(NonEmptyList.one(noAuthenticationMethod))

    def withUserPassword: List[Byte] =
      create(NonEmptyList.one(userPasswordAuthenticationMethod))

    def create(authMethods: NonEmptyList[Byte]): List[Byte] = {
      val nMethods = authMethods.length.toByte

      List[Byte](protocolVersion, nMethods) ++ authMethods.toList
    }

    def create(authMethod: Byte): List[Byte] =
      List[Byte](protocolVersion, 1, authMethod)
  }

  object Response {
    def withoutAuthentication: List[Byte] =
      List[Byte](protocolVersion, noAuthenticationMethod)

    def withNoAcceptableMethods: List[Byte] =
      List[Byte](protocolVersion, noAcceptableAuthenticationMethods)

    def withUserPassword: List[Byte] =
      List[Byte](protocolVersion, userPasswordAuthenticationMethod)

    def withWrongAuthMethod: List[Byte] = List[Byte](protocolVersion, -1)
  }
}
