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

import com.comcast.ip4s.{IpAddress, Port}

private[server] trait Socks5ServerCommandHandler[F[_]] {

  /** @param ipAddress
    *   received from client
    * @param port
    *   received from client
    * @param onConnectionSuccess
    *   must be call after success connection
    * @return
    *   pipe with input data from client and output data from destination
    */
  def handle(ipAddress: IpAddress, port: Port)(
      onConnectionSuccess: F[Unit]
  ): fs2.Pipe[F, Byte, Byte]
}
