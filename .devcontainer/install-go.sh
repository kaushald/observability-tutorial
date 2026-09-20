#!/usr/bin/env bash
# Installs only the Go toolchain into /usr/local/go.
# The devcontainer Go feature also compiles editor tools (gopls, dlv, staticcheck),
# which took three minutes on a 2-core Codespace. Nobody edits Go in this tutorial.
set -euo pipefail

GO_VERSION="1.25.14"
case "$(uname -m)" in
  x86_64)
    ARCH="amd64"
    SHA256="a21ae5633a269bcd7e90cf767e48225633795e99d831742cbf3397064fee7712"
    ;;
  aarch64 | arm64)
    ARCH="arm64"
    SHA256="9bf234ea70ffec9347fdf6b22ce4add51717d3386a38a441e8c8743fceb5eaee"
    ;;
  *)
    echo "Unsupported architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

if [ -x /usr/local/go/bin/go ] && /usr/local/go/bin/go version | grep -q "go$GO_VERSION "; then
  echo "Go $GO_VERSION already installed"
  exit 0
fi

SUDO=""
if [ "$(id -u)" != "0" ]; then
  SUDO="sudo"
fi

TARBALL="$(mktemp)"
curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-${ARCH}.tar.gz" -o "$TARBALL"
echo "$SHA256  $TARBALL" | sha256sum -c - >/dev/null
$SUDO rm -rf /usr/local/go
$SUDO tar -C /usr/local -xzf "$TARBALL"
rm -f "$TARBALL"
echo "Installed $(/usr/local/go/bin/go version) in ${SECONDS}s"
