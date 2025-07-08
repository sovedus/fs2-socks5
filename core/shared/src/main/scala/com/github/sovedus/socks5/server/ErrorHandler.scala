package com.github.sovedus.socks5.server

import cats.effect.Sync

import java.io.{ByteArrayOutputStream, PrintStream}

trait ErrorHandler[F[_]] {
  def handleException(t: Throwable): F[Unit]
}

object ErrorHandler {
  def noop[F[_]: Sync]: ErrorHandler[F] = _ => Sync[F].unit

  def stderr[F[_]: Sync]: ErrorHandler[F] = (t: Throwable) => {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)
    t.printStackTrace(ps)
    Sync[F].blocking(System.err.println(baos.toString))
  }
}
