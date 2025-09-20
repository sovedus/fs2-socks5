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

package io.github.sovedus.socks5.server.auth

import io.github.sovedus.socks5.common.ReadWriter
import io.github.sovedus.socks5.common.Socks5Exception.AuthenticationException
import io.github.sovedus.socks5.common.auth.{AuthenticationStatus, UserPasswordCredential}
import io.github.sovedus.socks5.server.credentials.CredentialStore

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalamock.stubs.CatsEffectStubs
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import fs2.Chunk

class UserPasswordAuthenticatorSpec
    extends AsyncFlatSpec
    with AsyncIOSpec
    with CatsEffectStubs
    with Matchers {

  "UserPasswordAuthenticator" should "successfully authenticate and send SUCCESS response according to RFC 1929" in {
    val credentialStoreStub = stub[CredentialStore[IO, UserPasswordCredential]]
    val rwStub = stub[ReadWriter[IO]]

    val authenticator = UserPasswordAuthenticator(credentialStoreStub)

    val authenticateVersion: Byte = 0x01

    val username = "test"
    val password = "test"

    for {
      _ <- rwStub.read2.succeedsWith((authenticateVersion, username.length.toByte))
      _ <- rwStub.read1.succeedsWith(password.length.toByte)
      _ <- (rwStub.readN _).returnsIOOnCall {
        case 1 => IO(Chunk.array(username.getBytes))
        case 2 => IO(Chunk.array(password.getBytes))
      }
      _ <- (credentialStoreStub.validate _).succeedsWith(true)
      _ <- (rwStub.write _).succeedsWith(())
      _ <- authenticator.authenticate(rwStub).asserting(_ shouldBe AuthenticationStatus.SUCCESS)
      calls <- (rwStub.write _).callsIO
    } yield calls shouldBe List(
      Chunk[Byte](authenticateVersion, AuthenticationStatus.SUCCESS.toByte)
    )
  }

  it should "throw AuthenticationException for unsupported authentication protocol version" in {
    val credentialStoreStub = stub[CredentialStore[IO, UserPasswordCredential]]
    val rwStub = stub[ReadWriter[IO]]

    val authenticator = UserPasswordAuthenticator(credentialStoreStub)

    val badAuthenticateVersion: Byte = 0x03

    val username = "test"

    for {
      _ <- rwStub.read2.succeedsWith((badAuthenticateVersion, username.length.toByte))
      _ <- authenticator.authenticate(rwStub).assertThrows[AuthenticationException]
    } yield {}
  }
}
