# Contributing

Issues, documentation improvements, tests, and code changes are welcome.

## Before opening a change

- Use an issue or discussion for large API or architecture changes.
- Keep pull requests focused and explain user-visible behavior.
- Do not report vulnerabilities publicly; follow [SECURITY.md](SECURITY.md).
- Avoid committing generated artifacts, credentials, or local configuration.

## Developer setup

Requirements:

- JDK 17 or newer;
- Docker for PostgreSQL integration tests;
- Git.

Run the full verification suite:

```bash
./mvnw verify
```

Run coverage and release checks:

```bash
./mvnw -Pcoverage,release verify
```

Generate and validate release SBOMs:

```bash
./mvnw -Psbom package
```

## Change requirements

- Follow the existing Java style: four-space indentation, braces on the same
  line, explicit imports, and no wildcard imports.
- Preserve Java 17 compatibility.
- Add or update automated tests for new behavior and bug fixes.
- Major functionality must include tests for its public behavior, boundary
  cases, and failure paths.
- Public API changes require Javadocs and migration notes.
- Dependency changes must pass dependency review and vulnerability scanning.
- Do not weaken validation, protection limits, CI permissions, or release
  integrity without an explicit security rationale.

Pull request titles follow Conventional Commits, for example:

```text
feat(core): add bounded operator support
fix(parser): reject malformed selector escapes
docs(security): clarify vulnerability reporting
```

## Developer Certificate of Origin

Every commit must include a `Signed-off-by` trailer certifying the
[Developer Certificate of Origin 1.1](https://developercertificate.org/).

Create signed-off commits with:

```bash
git commit --signoff
```

The sign-off means you have the legal right to submit the contribution under
the project's license. It is not a cryptographic signature.

## Review

Automated checks must pass before merge. Human review by a non-author is
required when the project has a second active maintainer. Until then, the sole
maintainer documents this limitation and does not use alternate accounts or
rubber-stamp reviews to manufacture compliance.

Maintainers review correctness, compatibility, tests, security impact,
dependency changes, documentation, and release implications.
