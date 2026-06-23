# Governance

`jpa-rsql-search` is currently a maintainer-led project.

## Roles

### Maintainer

Maintainers may triage issues, review changes, manage repository settings,
publish releases, respond to vulnerability reports, and appoint additional
maintainers.

Maintainers must:

- use multi-factor authentication;
- follow least privilege;
- protect release and signing credentials;
- disclose conflicts of interest;
- document security-relevant exceptions;
- avoid unilateral changes that bypass required checks.

### Contributor

Contributors propose changes through issues, discussions, and pull requests.
Contributions require DCO sign-off and are reviewed under
[CONTRIBUTING.md](CONTRIBUTING.md).

## Decisions

Routine decisions are made through reviewed pull requests. Significant API,
security, release, or governance changes should have a public issue or
discussion before implementation.

When more than one maintainer is active, decisions seek consensus. If consensus
cannot be reached, the maintainer responsible for the affected area decides and
records the rationale publicly.

## Maintainer access

Elevated access is granted only after sustained, constructive contributions and
a review of the candidate's need, security practices, and ability to maintain
the project. Permissions start at the lowest practical level.

The current access list is maintained in [MAINTAINERS.md](MAINTAINERS.md).

## Continuity

The project currently has a bus factor of one. This is a documented limitation,
not a target state. The project will seek a second maintainer before claiming
OpenSSF maturity levels that require independent review or continuity.

Repository history, build instructions, release automation, policy, and
security response procedures are kept in the public repository to reduce
handover risk.
