# fs2-socks5

---

## ⚠️ Warning
### This library is currently in development. APIs are not yet stable and may undergo significant changes. Use at your own risk.

---

This library provides SOCKS5 client and server implementations.

## Getting Started

```scala
libraryDependencies += "io.github.sovedus" %% "fs2-socks5" % "0.1.0" // for JVM
libraryDependencies += "io.github.sovedus" %%% "fs2-socks5" % "0.1.0" // for Scala Native
```



## Example:
[look here](/example/src/main/scala/io/github/sovedus/socks5/example)

## Commands support:
- [x] `CONNECT`
- [ ] `BIND`
- [ ] `UDP_ASSOCIATE` (wait native UPD support in fs2-io)



