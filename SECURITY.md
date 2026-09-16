# Security policy

**InVxTermux** (`com.involvex.termux_app`) is an unofficial fork of
[termux/termux-app](https://github.com/termux/termux-app).

## Reporting a vulnerability in this fork

Please report privately via GitHub Security Advisories for
[`involvex/termux-app`](https://github.com/involvex/termux-app/security/advisories/new)
when possible. Do not open a public issue for unfixed vulnerabilities.

## Upstream Termux

If the issue also affects official Termux (or is clearly an upstream defect),
prefer the Termux project’s process:

- https://termux.dev/security

Core Linux packages inside the environment are maintained in
[`termux/termux-packages`](https://github.com/termux/termux-packages) — report
package security issues there when appropriate.

## Signing / updates

GitHub release APKs for this fork are typically signed with a **public test
key**. Treat them like debug builds: only install from a source you trust, and
do not mix APKs from different publishers that share `sharedUserId`.
