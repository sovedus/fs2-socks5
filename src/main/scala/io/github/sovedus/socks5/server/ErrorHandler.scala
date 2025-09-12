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

import cats.effect.Sync

import org.typelevel.log4cats.LoggerFactory

trait ErrorHandler[F[_]] {
  def handleException(t: Throwable): F[Unit]
}

object ErrorHandler {
  def noop[F[_]: Sync]: ErrorHandler[F] = _ => Sync[F].unit

  def default[F[_]: LoggerFactory]: ErrorHandler[F] = {
    val logger = LoggerFactory[F].getLoggerFromClass(ErrorHandler.getClass)

    (t: Throwable) => logger.error(t)("Handle connection error")
  }
}
