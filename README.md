# secon-gateway

[![build](https://github.com/bitmarck-service/http4s-secon/actions/workflows/build.yml/badge.svg)](https://github.com/bitmarck-service/http4s-secon/actions/workflows/build.yml)
[![Release Notes](https://img.shields.io/github/release/bitmarck-service/http4s-secon.svg?maxAge=3600)](https://github.com/bitmarck-service/http4s-secon/releases/latest)
[![Apache License 2.0](https://img.shields.io/github/license/bitmarck-service/http4s-secon.svg?maxAge=3600)](https://www.apache.org/licenses/LICENSE-2.0)

["Verschlüsselung nach GKV Datenaustausch" (SECON)](https://gkv-datenaustausch.de/media/dokumente/standards_und_normen/technische_spezifikationen/Anlage_16_-_Security_Schnittstelle.pdf)
Encryption/Decryption Proxy.

## Example Configuration

```json
{
  "serverAddress": "0.0.0.0:8080",
  "keyStorePath": "keyStore.p12",
  "password": "pw",
  "ldapUri": "10.55.60.97:389",
  "uri": "https://example.com"
}
```

# http4s-secon

[![build](https://github.com/bitmarck-service/http4s-secon/actions/workflows/build.yml/badge.svg)](https://github.com/bitmarck-service/http4s-secon/actions/workflows/build.yml)
[![Release Notes](https://img.shields.io/github/release/bitmarck-service/http4s-secon.svg?maxAge=3600)](https://github.com/bitmarck-service/http4s-secon/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/de.bitmarck.bms/http4s-secon_2.13)](https://search.maven.org/artifact/de.bitmarck.bms/http4s-secon_2.13)
[![Apache License 2.0](https://img.shields.io/github/license/bitmarck-service/http4s-secon.svg?maxAge=3600)](https://www.apache.org/licenses/LICENSE-2.0)

["Verschlüsselung nach GKV Datenaustausch" (SECON)](https://gkv-datenaustausch.de/media/dokumente/standards_und_normen/technische_spezifikationen/Anlage_16_-_Security_Schnittstelle.pdf)
for [Http4s](https://http4s.org/) using [DieTechniker/secon-tool](https://github.com/DieTechniker/secon-tool).

## Usage

### build.sbt

```sbt
libraryDependencies += "de.bitmarck.bms" %% "http4s-secon" % "0.1.0"
```

## License

This project uses the Apache 2.0 License. See the file called LICENSE.
