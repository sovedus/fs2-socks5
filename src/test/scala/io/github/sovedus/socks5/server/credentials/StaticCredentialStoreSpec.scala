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

package io.github.sovedus.socks5.server.credentials

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalamock.stubs.CatsEffectStubs
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class StaticCredentialStoreSpec
    extends AsyncFlatSpec
    with CatsEffectStubs
    with AsyncIOSpec
    with Matchers {

  "StaticCredentialStore" should "return true for valid user credentials" in {
    val users = Map("test" -> "test")
    val store = StaticCredentialStore[IO](users)
    store.validate(UserPasswordCredential("test", "test")).asserting(_ shouldBe true)
  }

  it should "return false for existing username with incorrect password" in {
    val users = Map("test" -> "test")
    val store = StaticCredentialStore[IO](users)
    store.validate(UserPasswordCredential("test", "123")).asserting(_ shouldBe false)
  }

  it should "reject authentication for unknown username with any password" in {
    val users = Map("test" -> "test")
    val store = StaticCredentialStore[IO](users)
    store.validate(UserPasswordCredential("user", "123")).asserting(_ shouldBe false)
  }
}
