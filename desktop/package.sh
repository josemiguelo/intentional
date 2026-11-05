#!/bin/sh
# Builds the desktop guard and stages a release: desktop/build/dist/
# intentional-<version>/ holding bin/intentional, extension/ (browser/),
# intentional.service and install.sh. With --archive it also packs
# intentional-<version>-linux-x64.tar.gz and its .sha256 there, as the
# release workflow publishes them. Prints the staged directory.
set -eu

repo=$(cd "$(dirname "$0")/.." && pwd)
(cd "$repo" && ./gradlew -q :desktop:linkReleaseExecutableLinuxX64)
exe="$repo/desktop/build/bin/linuxX64/releaseExecutable/intentional.kexe"
version=$("$exe" --version | cut -d' ' -f2)

out="$repo/desktop/build/dist"
name="intentional-$version"
rm -rf "${out:?}/$name"
mkdir -p "$out/$name/bin" "$out/$name/extension"
install -m755 "$exe" "$out/$name/bin/intentional"
cp -r "$repo/browser/." "$out/$name/extension/"
install -m644 "$repo/desktop/dist/intentional.service" "$out/$name/"
install -m755 "$repo/desktop/dist/install.sh" "$out/$name/"

if [ "${1:-}" = --archive ]; then
  archive="$name-linux-x64.tar.gz"
  (cd "$out" && tar -czf "$archive" "$name" && sha256sum "$archive" >"$archive.sha256")
fi
echo "$out/$name"
