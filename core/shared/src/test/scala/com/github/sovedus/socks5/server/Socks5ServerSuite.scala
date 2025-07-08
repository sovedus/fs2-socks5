package com.github.sovedus.socks5.server

import cats.effect.IO
import com.comcast.ip4s._
import com.github.sovedus.socks5.server.auth.NoAuthAuthenticator
import fs2.{Chunk, Pipe}
import fs2.io.net.Network
import munit.CatsEffectSuite

class Socks5ServerSuite extends CatsEffectSuite {

  test("should return NO_ACCEPTABLE_METHODS when authentication method not supported") {
    val handshakeReq = List[Byte](5, 1, 2)
    val handshakeResp = List[Byte](5, 0xff.toByte)

    Socks5ServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"0")
      .withAuthenticator(NoAuthAuthenticator())
      .withErrorHandler(ErrorHandler.noop)
      .build
      .use { server =>
        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
          for {
            _ <- clientSocket.write(Chunk.from(handshakeReq))
            _ <- clientSocket.readN(2).map(_.toList).assertEquals(handshakeResp)
          } yield {}
        }
      }
  }

  test("should successfully complete data exchange using CONNECT command") {
    val handshakeReq = List[Byte](5, 1, 0)
    val handshakeResp = List[Byte](5, 0)

    val commandReq = List[Byte](5, 1, 0, 1, 127, 0, 0, 1, 1, -69)
    val commandResp = List[Byte](5, 0, 0, 1, 127, 0, 0, 1, 1, -69)

    val testDataReq = List[Byte](1, 2, 3, 4, 5)
    val testDataResp = testDataReq.reverse

    Socks5ServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"0")
      .withAuthenticator(NoAuthAuthenticator())
      .withErrorHandler(ErrorHandler.noop)
      .withConnectionHandler(commandHandler(testDataResp))
      .build
      .use { server =>
        Network[IO].client(SocketAddress(server.host, server.port)).use { clientSocket =>
          for {
            _ <- clientSocket.write(Chunk.from(handshakeReq))
            _ <- clientSocket.readN(2).map(_.toList).assertEquals(handshakeResp)
            _ <- clientSocket.write(Chunk.from(commandReq))
            _ <- clientSocket.readN(10).map(_.toList).assertEquals(commandResp)
            _ <- clientSocket.write(Chunk.from(testDataReq))
            _ <- clientSocket
              .readN(testDataResp.length)
              .map(_.toList)
              .assertEquals(testDataResp)
          } yield {}
        }
      }
  }

  private def commandHandler(sendData: List[Byte]): Socks5ServerCommandHandler[IO] =
    new Socks5ServerCommandHandler[IO] {
      override def handle(ipAddress: IpAddress, port: Port)(
          onConnectionSuccess: IO[Unit]
      ): Pipe[IO, Byte, Byte] = { in =>
        fs2
          .Stream
          .eval(onConnectionSuccess)
          .flatMap(_ => fs2.Stream.emits(sendData).concurrently(in.delete(_ => true)))
      }
    }

}
