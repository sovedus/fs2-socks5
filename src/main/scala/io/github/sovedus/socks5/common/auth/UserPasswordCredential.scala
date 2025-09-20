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

package io.github.sovedus.socks5.common.auth

case class UserPasswordCredential private (user: String, password: String)

object UserPasswordCredential {
  def apply(user: String, password: String): UserPasswordCredential = {
    val userBytes = user.getBytes
    val passwordBytes = password.getBytes

    if (userBytes.isEmpty && passwordBytes.isEmpty)
      throw new IllegalArgumentException("Username and password cannot be empty")
    if (userBytes.length < 1) throw new IllegalArgumentException("Username cannot be empty")
    else if (userBytes.length > 255)
      throw new IllegalArgumentException("Username is too long")
    else if (passwordBytes.length < 1)
      throw new IllegalArgumentException("Password cannot be empty")
    else if (passwordBytes.length > 255)
      throw new IllegalArgumentException("Password is too long")

    new UserPasswordCredential(user, password)
  }
}
