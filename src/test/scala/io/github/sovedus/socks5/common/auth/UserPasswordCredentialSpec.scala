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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class UserPasswordCredentialSpec extends AnyFlatSpec with Matchers {

  "UserPasswordCredential" should "allow creation with non-empty username and password" in {
    noException should be thrownBy UserPasswordCredential("test", "test")
  }

  it should "not throw an exception when user and password are 255 single-byte chars long" in {
    val user = Random.alphanumeric.take(255).mkString
    val password = Random.alphanumeric.take(255).mkString

    noException should be thrownBy UserPasswordCredential(user, password)
  }

  it should "throw an IllegalArgumentException with correct message when username and password are empty" in {
    the[IllegalArgumentException] thrownBy {
      UserPasswordCredential("", "")
    } should have message "Username and password cannot be empty"
  }

  it should "throw an IllegalArgumentException when username is empty but password is provided" in {
    val password = "password"

    the[IllegalArgumentException] thrownBy {
      UserPasswordCredential("", password)
    } should have message "Username cannot be empty"
  }

  it should "throw an IllegalArgumentException when username exceeds 255 bytes" in {
    val user = Random.alphanumeric.take(260).mkString
    val password = "password"

    the[IllegalArgumentException] thrownBy {
      UserPasswordCredential(user, password)
    } should have message "Username is too long"
  }

  it should "throw an IllegalArgumentException when password is empty but username is provided" in {
    val user = "test"

    the[IllegalArgumentException] thrownBy {
      UserPasswordCredential(user, "")
    } should have message "Password cannot be empty"
  }

  it should "throw an IllegalArgumentException when password exceeds 255 bytes" in {
    val user = "test"
    val password = Random.alphanumeric.take(260).mkString

    the[IllegalArgumentException] thrownBy {
      UserPasswordCredential(user, password)
    } should have message "Password is too long"
  }
}
