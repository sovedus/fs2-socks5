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

package io.github.sovedus.socks5.server

import cats.data.OptionT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

import java.io.EOFException

import fs2.Chunk
import fs2.concurrent.SignallingRef
import fs2.io.net.Network

class Socks5ServerConnectCommandHandlerSpec
    extends AsyncFlatSpec
    with AsyncIOSpec
    with Matchers {

  "Socks5ServerConnectCommandHandler" should "establish bidirectional data transfer between client and server" in {
    val handler = new Socks5ServerConnectCommandHandler[IO]()

    val clientData = "client_hello"
    val serverData = "server_hello"

    val transferTuple = for {
      (serverAddress, serverSockets) <- Network[IO].serverResource()
      commandPipe <- handler.handle(serverAddress.host, serverAddress.port)
    } yield (serverSockets, commandPipe)

    transferTuple.use {
      case (serverSockets, commandPipe) =>
        for {
          signal <- SignallingRef[IO].of(false)
          _ <- serverSockets
            .interruptWhen(signal)
            .evalMap { socket =>
              for {
                _ <- OptionT(socket.read(100))
                  .getOrRaise(new EOFException())
                  .map(c => new String(c.toArray))
                  .asserting(_ should equal(clientData))
                _ <- socket.write(Chunk.array(serverData.getBytes))
              } yield {}
            }
            .onFinalize(signal.set(true))
            .compile
            .drain
            .start
          _ <- fs2.Stream
            .emit(clientData)
            .covary[IO]
            .through(fs2.text.utf8.encode)
            .through(commandPipe)
            .take(serverData.length.toLong)
            .interruptAfter(100.millis)
            .through(fs2.text.utf8.decode)
            .compile
            .string
            .map(_ should equal(serverData))
        } yield {}
    }
  }
}
