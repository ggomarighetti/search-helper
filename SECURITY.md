# Security Policy

## Supported versions

The latest stable major release receives security fixes. After a new major
release, the previous major receives security-only fixes for six months.
Older releases are unsupported.

Pre-release and snapshot builds are development artifacts and should not be
used in production.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability.

Use GitHub's private vulnerability reporting form:

https://github.com/ggomarighetti/jpa-rsql-search/security/advisories/new

Include, when possible:

- the affected version and module;
- impact and expected attacker capabilities;
- a minimal reproducer or proof of concept;
- suggested mitigations;
- whether the report may credit you publicly.

The project aims to:

- acknowledge reports within 3 business days;
- provide an initial assessment within 7 business days;
- keep the reporter informed at least every 14 days;
- coordinate disclosure within 90 days, or sooner when a fix is available.

Target remediation times after confirmation are 14 days for critical issues,
30 days for high severity issues, and 60 days for medium severity issues.
Actual timing may vary with complexity and safe-release requirements.

## Disclosure and advisories

Confirmed vulnerabilities are handled through a private GitHub Security
Advisory. Public advisories identify affected versions, impact, mitigations,
fixed versions, and reporter credit unless anonymity was requested. A CVE is
requested when appropriate.

Security fixes are called out explicitly in release notes. The project will not
silently suppress a confirmed exploitable vulnerability.

## Dependency and static-analysis findings

Critical or high severity exploitable findings block a release. Exceptions must
be documented with scope, rationale, owner, and a review date. Findings that do
not affect this project should be documented as non-exploitable and, when a
release contains the affected component, represented in VEX data.

See [Dependency Policy](docs/DEPENDENCY_POLICY.md) for dependency and license
handling.

## Release verification

Published Maven Central artifacts are signed with PGP. GitHub releases also
publish checksums, CycloneDX SBOMs, and GitHub artifact attestations when the
release workflow is used.

Verification instructions are in
[Release Security](docs/RELEASE_SECURITY.md).
