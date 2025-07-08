package com.github.sovedus.socks5.common

import cats.effect.Sync
import com.comcast.ip4s.{Dns, Hostname, IpAddress}

trait Resolver[F[_]] {
  def resolve(hostname: Hostname): F[IpAddress]
}

object Resolver {
  def default[F[_]: Sync]: Resolver[F] = new Resolver[F] {
    private val dns = Dns.forSync[F]

    override def resolve(hostname: Hostname): F[IpAddress] = dns.resolve(hostname)
  }
}
