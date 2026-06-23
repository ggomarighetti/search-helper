# Changelog

All notable changes to this project are documented here. Releases follow
Semantic Versioning and release notes are maintained from Conventional Commits.

## [2.0.0](https://github.com/ggomarighetti/jpa-rsql-search/compare/v1.0.1...v2.0.0) (2026-06-23)


### ⚠ BREAKING CHANGES

* introduce the modular v2 architecture ([#26](https://github.com/ggomarighetti/jpa-rsql-search/issues/26))

### Features

* harden search validation and rsql extensibility ([#24](https://github.com/ggomarighetti/jpa-rsql-search/issues/24)) ([e7c236a](https://github.com/ggomarighetti/jpa-rsql-search/commit/e7c236ac22592e7cce145fae6b8e7fb8c9829b22))
* introduce the modular v2 architecture ([#26](https://github.com/ggomarighetti/jpa-rsql-search/issues/26)) ([207bd2e](https://github.com/ggomarighetti/jpa-rsql-search/commit/207bd2e2307aba2b1d70f67169017f4bf888c97e))
* **security:** adopt OpenSSF supply-chain controls ([#27](https://github.com/ggomarighetti/jpa-rsql-search/issues/27)) ([9dc72a6](https://github.com/ggomarighetti/jpa-rsql-search/commit/9dc72a6f52a69893bb7b773e2424cca653a5857d))


### Bug Fixes

* **security:** keep OSV scanning compatible with SHA enforcement ([8093b77](https://github.com/ggomarighetti/jpa-rsql-search/commit/8093b77e44632792f339955e03dc72eb04f3184c))
* **security:** pin ClusterFuzzLite base image ([#29](https://github.com/ggomarighetti/jpa-rsql-search/issues/29)) ([93c22ac](https://github.com/ggomarighetti/jpa-rsql-search/commit/93c22ac6ff439ec5618a542d784498de4637b26e))


### Documentation

* organize repository documentation ([#31](https://github.com/ggomarighetti/jpa-rsql-search/issues/31)) ([0eeeff6](https://github.com/ggomarighetti/jpa-rsql-search/commit/0eeeff609925e61309acd02c71ae123ee0c071a3))


### Dependencies

* **deps:** bump the maven-dependencies group across 1 directory with 3 updates ([#38](https://github.com/ggomarighetti/jpa-rsql-search/issues/38)) ([d744905](https://github.com/ggomarighetti/jpa-rsql-search/commit/d7449055e6e413c800f471031816bd616c83ff1c))

## [1.0.1](https://github.com/ggomarighetti/jpa-rsql-search/releases/tag/v1.0.1) (2026-06-18)

### Release Metadata

- Correct the published developer name to Guillermo Orue Marighetti.
- Publish release artifacts to GitHub Packages in addition to Maven Central.

## [1.0.0](https://github.com/ggomarighetti/jpa-rsql-search/releases/tag/v1.0.0) (2026-06-17)

### Features

- Define a stable Spring-friendly contract for guarded RSQL to JPA
  `Specification` compilation.
- Provide `SearchDefinition<T>` as the public boundary for exposed selectors,
  entity paths, filtering, sorting, paging, query text, validation rules, and
  protection limits.
- Include bounded RSQL parsing, operator whitelisting, value conversion,
  structured validation errors, and mandatory application-owned predicates.
- Ship Spring Boot auto-configuration and configuration metadata for
  `jpa.rsql.search.*` properties.
- Support Java 17+, Spring Boot 4.x, Spring Data JPA, Hibernate Validator, and
  PostgreSQL-backed integration coverage.

### Release Infrastructure

- Publish `io.github.ggomarighetti:jpa-rsql-search:1.0.0` to Maven Central via
  Central Portal and JReleaser.
- Generate and validate release Javadocs, source artifacts, PGP signatures, and
  Maven Central checksums.
- Manage future release PRs with Release Please and Conventional Commits.
