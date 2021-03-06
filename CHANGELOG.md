# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [6.0.0] - 2020-12-04
### Added
- Accept request payload as java.io.InputStream for Java. Since InputStream in general can only be consumed once, here are limitations of using stream payload:
  - mauth-signer generates the signature for v2 only even the both v1 and v2 are required.
  - mauth-authenticator doesn't support "Fall back to V1 authentication when V2 authentication fails".

## [5.0.2] - 2020-11-05
### Changed
- Update library versions
### Fixed
- convert `TestFixtures` to java class, was causing cross-compilation problems 

## [5.0.1] - 2020-08-07
### Changed
- Change the default signing versions to 'v1' only

## [5.0.0] - 2020-07-14
### Changed
- Replace `V2_ONLY_SIGN_REQUESTS` option with `MAUTH_SIGN_VERSIONS` option and set the default to `v2` only

## [4.0.2] - 2020-06-10

## [4.0.1] - 2020-06-09
### Changed
- Use the encoded resource path and query parameters for Mauth singer
- Fall back to V1 authentication when V2 authentication fails using Akka Http

## [4.0.0] - 2020-03-19
### Added
- scalac flags for 2.12 and 2.13
- Address deprecation warnings, silence
- Use sbt-smartrelease plugin

### Remove
- unused `sbt-mima-plugin`, `scalastyle-sbt-plugin` plugins

## [3.0.0]
### Added
- Add cross compilation for 2.12 and 2.13

## [2.1.0]
### Changed
- Fall back to V1 authentication when V2 authentication fails.

## [2.0.5]
### Added
- Add support for MWSV2 protocol in Java modules
- Add support for MWSV2 protocol in Scala modules

### Deprecated
- All method and helper functions that deal with only MAuth V1 headers has been deprecated. 
  Read the deprecation message of each for how to migrate.
