package io.github.sovedus.socks5.example

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import io.github.sovedus.socks5.server.Socks5ServerBuilder
import io.github.sovedus.socks5.server.auth.NoAuthAuthenticator
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Server extends IOApp.Simple {

  implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]

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
