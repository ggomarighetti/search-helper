# Changelog

All notable changes to this project are documented here. Releases follow
Semantic Versioning and release notes are maintained from Conventional Commits.

## [2.0.0](https://github.com/ggomarighetti/rsql-jpa-search/compare/v1.0.1...v2.0.0) (2026-06-24)

### Maintainer Note

Thanks for the feedback received after the first release.

Version 2.0.0 is the step where `rsql-jpa-search` stops being source code
extracted from a module inside a REST API project and becomes the library shape
it deserved from the beginning: a multi-module Maven architecture, clearer
package boundaries, CI, compatibility checks, fuzz testing, SBOM/security
metadata, and an initial documentation set.

This release also adds the standard project files expected from an open source
library, including contributing guidelines, changelog, code of conduct,
security/support docs, governance notes, and release documentation.

As part of that work, the library was renamed from `jpa-rsql-search` to
`rsql-jpa-search`. The new name follows the naming style already used by
related RSQL libraries such as `rsql-parser` and `rsql-jpa-specification`.

Because of this rename and the v2 modular architecture, version 2.0.0 is
intentionally breaking compared with 1.0.x. Maven coordinates, Java imports,
Spring Boot properties, and auto-configuration names changed from the 1.0.x
line. Please read the updated Getting Started section and documentation before
upgrading.

### ⚠ BREAKING CHANGES

* Rename the project and repository from `jpa-rsql-search` to
  `rsql-jpa-search`.
* Rename published Maven modules from `jpa-rsql-search-*` to
  `rsql-jpa-search-*`.
* Move the Java namespace from
  `io.github.ggomarighetti.jparsqlsearch` to
  `io.github.ggomarighetti.rsqljpasearch`.
* Rename the Spring Boot property prefix from `jpa.rsql.search` to
  `rsql.jpa.search`.
* Rename Spring Boot auto-configuration types to use the `RsqlJpaSearch`
  prefix.

### refactor

* rename project to rsql-jpa-search ([#39](https://github.com/ggomarighetti/rsql-jpa-search/issues/39)) ([fcea9e8](https://github.com/ggomarighetti/rsql-jpa-search/commit/fcea9e8f63cb81131d22bf1cfc7f96fc3ab3193f))


### Features

* harden search validation and rsql extensibility ([#24](https://github.com/ggomarighetti/rsql-jpa-search/issues/24)) ([e7c236a](https://github.com/ggomarighetti/rsql-jpa-search/commit/e7c236ac22592e7cce145fae6b8e7fb8c9829b22))
* introduce the modular v2 architecture ([#26](https://github.com/ggomarighetti/rsql-jpa-search/issues/26)) ([207bd2e](https://github.com/ggomarighetti/rsql-jpa-search/commit/207bd2e2307aba2b1d70f67169017f4bf888c97e))
* **security:** adopt OpenSSF supply-chain controls ([#27](https://github.com/ggomarighetti/rsql-jpa-search/issues/27)) ([9dc72a6](https://github.com/ggomarighetti/rsql-jpa-search/commit/9dc72a6f52a69893bb7b773e2424cca653a5857d))


### Bug Fixes

* **security:** keep OSV scanning compatible with SHA enforcement ([8093b77](https://github.com/ggomarighetti/rsql-jpa-search/commit/8093b77e44632792f339955e03dc72eb04f3184c))
* **security:** pin ClusterFuzzLite base image ([#29](https://github.com/ggomarighetti/rsql-jpa-search/issues/29)) ([93c22ac](https://github.com/ggomarighetti/rsql-jpa-search/commit/93c22ac6ff439ec5618a542d784498de4637b26e))


### Documentation

* add pull request standards ([#42](https://github.com/ggomarighetti/rsql-jpa-search/issues/42)) ([b74b46a](https://github.com/ggomarighetti/rsql-jpa-search/commit/b74b46acde104c0334aa1a3350fdf6a94dd77f5d))
* organize repository documentation ([#31](https://github.com/ggomarighetti/rsql-jpa-search/issues/31)) ([0eeeff6](https://github.com/ggomarighetti/rsql-jpa-search/commit/0eeeff609925e61309acd02c71ae123ee0c071a3))
* streamline README and split reference docs ([#40](https://github.com/ggomarighetti/rsql-jpa-search/issues/40)) ([c3ac333](https://github.com/ggomarighetti/rsql-jpa-search/commit/c3ac33330196939770e8b6c747f3eb16e593693e))


### Dependencies

* **deps:** bump the maven-dependencies group across 1 directory with 3 updates ([#38](https://github.com/ggomarighetti/rsql-jpa-search/issues/38)) ([d744905](https://github.com/ggomarighetti/rsql-jpa-search/commit/d7449055e6e413c800f471031816bd616c83ff1c))

## [1.0.1](https://github.com/ggomarighetti/rsql-jpa-search/releases/tag/v1.0.1) (2026-06-18)

### Release Metadata

- Correct the published developer name to Guillermo Orue Marighetti.
- Publish release artifacts to GitHub Packages in addition to Maven Central.

## [1.0.0](https://github.com/ggomarighetti/rsql-jpa-search/releases/tag/v1.0.0) (2026-06-17)

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
