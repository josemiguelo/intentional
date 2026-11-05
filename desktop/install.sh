#!/bin/sh
# Builds the desktop guard from this checkout and installs it, through the
# same install.sh a release carries (desktop/dist/install.sh). Machines
# without a checkout install a release instead (see docs/DESKTOP.md).
#
#   desktop/install.sh [install]   build and install
#   desktop/install.sh check       everything in place? (no build)
set -eu

case "${1:-install}" in
check) exec sh "$(dirname "$0")/dist/install.sh" check ;;
install)
  staged=$(sh "$(dirname "$0")/package.sh" | tail -n 1)
  exec sh "$staged/install.sh" install
  ;;
*) echo "usage: $0 [install|check]" >&2; exit 2 ;;
esac
