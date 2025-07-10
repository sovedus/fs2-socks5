package io.github.sovedus.socks5.example

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import io.github.sovedus.socks5.server.Socks5ServerBuilder
import io.github.sovedus.socks5.server.auth.NoAuthAuthenticator

object Server extends IOApp.Simple {

  override def run: IO[Unit] = {
    Socks5ServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"1080")
      .withAuthenticator(NoAuthAuthenticator())
      .build
      .useForever
    // Run curl -x socks5://localhost:1080 https://google.com
  }
}
