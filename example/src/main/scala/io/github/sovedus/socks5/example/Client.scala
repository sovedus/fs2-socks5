package io.github.sovedus.socks5.example

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import fs2.concurrent.SignallingRef
import io.github.sovedus.socks5.client.Socks5ClientBuilder

import scala.concurrent.duration.DurationInt

object Client extends IOApp.Simple {

  override def run: IO[Unit] = {

    // Use your socks5 server host and port
    val client = Socks5ClientBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"8118")
      .withResolveHostOnServer
      .build

    val httpRawReq = """GET / HTTP/1.1
                       |Host: echo.free.beeceptor.com
                       |User-Agent: curl/8.14.1
                       |Accept: */*
                       |
                       |""".stripMargin

    for {
      signal <- SignallingRef[IO].of(false)
      _ <- (IO.sleep(2.seconds) >> signal.set(true)).start
      _ <- fs2
        .Stream
        .emit(httpRawReq)
        .through(fs2.text.utf8.encode)
        .through(client.connect(host"echo.free.beeceptor.com", port"80"))
        .through(fs2.text.utf8.decode)
        .interruptWhen(signal)
        .printlns
        .compile
        .drain
    } yield {}
  }
}
