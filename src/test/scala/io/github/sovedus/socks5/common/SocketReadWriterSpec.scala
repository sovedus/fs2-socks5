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

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalamock.stubs.CatsEffectStubs
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

import fs2.io.net.Socket

class SocketReadWriterSpec extends AsyncFlatSpec with AsyncIOSpec with CatsEffectStubs {

  "SocketReadWriter" should "throw TimeoutException when socket read operation times out" in {
    val socketStub = stub[Socket[IO]]

    val rw = ReadWriter.fromSocket(socketStub, 1.second)

    for {
      _ <- (socketStub.reads _).returnsIOWith(fs2.Stream.never[IO])
      _ <- rw.reads.take(1).compile.drain.assertThrows[TimeoutException]
    } yield {}
  }

  it should "throw TimeoutException when socket write operation times out" in {
    val socketStub = stub[Socket[IO]]

    val rw = ReadWriter.fromSocket(socketStub, 1.second)

    for {
      _ <- (socketStub.writes _).returnsIOWith(_ => fs2.Stream.never[IO])
      _ <- fs2.Stream
        .emit(1.toByte)
        .through(rw.writes)
        .compile
        .drain
        .assertThrows[TimeoutException]
    } yield {}
  }

}
