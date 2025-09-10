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

//package io.github.sovedus.socks5.server
//
//import io.github.sovedus.socks5.server.auth.NoAuthAuthenticator
//import cats.effect.{IO, Resource}
//import com.comcast.ip4s.*
//import fs2.{Chunk, Pipe}
//import fs2.io.net.Network
//import munit.CatsEffectSuite
//import org.typelevel.log4cats.LoggerFactory
//import org.typelevel.log4cats.noop.NoOpFactory
//
//class Socks5ServerSuite extends CatsEffectSuite {
//
//  private implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]
//
//  test("should return NO_ACCEPTABLE_METHODS when authentication method not supported") {
//    val handshakeReq = List[Byte](5, 1, 2)
//    val handshakeResp = List[Byte](5, 0xff.toByte)
//
//    Socks5ServerBuilder
//      .default[IO]
//      .withHost(host"localhost")
//      .withPort(port"0")
//      .withAuthenticator(NoAuthAuthenticator())
//      .withErrorHandler(ErrorHandler.noop)
//      .build
//      .use { server =>
//        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
//          for {
//            _ <- clientSocket.write(Chunk.from(handshakeReq))
//            _ <- clientSocket.readN(2).map(_.toList).assertEquals(handshakeResp)
//          } yield {}
//        }
//      }
//  }
//
//  test("should successfully complete data exchange using CONNECT command") {
//    val handshakeReq = List[Byte](5, 1, 0)
//    val handshakeResp = List[Byte](5, 0)
//
//    val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
//    val commandResp = List[Byte](5, 0, 0, 1, 127, 0, 0, 1, 1, -69)
//
//    val testDataReq = List[Byte](1, 2, 3, 4, 5)
//    val testDataResp = testDataReq.reverse
//
//    Socks5ServerBuilder
//      .default[IO]
//      .withHost(host"localhost")
//      .withPort(port"0")
//      .withAuthenticator(NoAuthAuthenticator())
//      .withErrorHandler(ErrorHandler.noop)
//      .withConnectionHandler(commandHandler(testDataResp))
//      .build
//      .use { server =>
//        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
//          for {
//            _ <- clientSocket.write(Chunk.from(handshakeReq))
//            _ <- clientSocket.readN(2).map(_.toList).assertEquals(handshakeResp)
//            _ <- clientSocket.write(Chunk.from(commandReq))
//            _ <- clientSocket.readN(10).map(_.toList).assertEquals(commandResp)
//            _ <- clientSocket.write(Chunk.from(testDataReq))
//            _ <- clientSocket
//              .readN(testDataResp.length)
//              .map(_.toList)
//              .assertEquals(testDataResp)
//          } yield {}
//        }
//      }
//  }
//
//  private def commandHandler(sendData: List[Byte]): Socks5ServerCommandHandler[IO] =
//    new Socks5ServerCommandHandler[IO] {
//      override def handle(
//          targetIp: IpAddress,
//          targetPort: Port
//      ): Resource[IO, Pipe[IO, Byte, Byte]] =
//        Resource
//          .pure(fs2.Stream.emits(sendData).covary[IO])
//          .map(stream => in => stream.concurrently(in.delete(_ => true)))
//    }
//
//}
