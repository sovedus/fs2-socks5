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

import io.github.sovedus.socks5.common.Socks5Exception.{
  IncompleteReadException,
  ReachedEndOfStream
}

import cats.data.OptionT
import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*

import scala.concurrent.duration.FiniteDuration

import fs2.{Chunk, Pipe}
import fs2.io.net.Socket

trait ReadWriter[F[_]] extends Reader[F] with Writer[F]

object ReadWriter {

  private class SocketReadWriter[F[_]](socket: Socket[F], t: FiniteDuration)(
      implicit F: Async[F]
  ) extends ReadWriter[F] {

    override def reads: fs2.Stream[F, Byte] = socket.reads.timeoutOnPull(t)

    override def readN(n: Int): F[Chunk[Byte]] = {
      OptionT(socket.read(n))
        .timeout(t)
        .getOrRaise(ReachedEndOfStream())
        .flatTap(c =>
          F.raiseWhen(c.size != n)(
            IncompleteReadException(n, c.size)
          )
        )
    }

    override def read1: F[Byte] = readN(1).map(c => c(0))

    override def read2: F[(Byte, Byte)] = readN(2).map(c => (c(0), c(1)))

    override def read4: F[(Byte, Byte, Byte, Byte)] =
      readN(4).map(c => (c(0), c(1), c(2), c(3)))

    override def writes: Pipe[F, Byte, Nothing] = socket.writes(_).timeoutOnPull(t)

    override def write(chunk: Chunk[Byte]): F[Unit] = socket.write(chunk).timeout(t)
  }

  def fromSocket[F[_]: Async](socket: Socket[F], t: FiniteDuration): ReadWriter[F] =
    new SocketReadWriter[F](socket, t)
}
