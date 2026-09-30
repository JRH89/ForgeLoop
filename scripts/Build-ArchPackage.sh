#!/usr/bin/env bash
set -euo pipefail

# Build a real pacman package from jpackage's native app-image. makepkg only
# stages files here; the Java payload itself was already built on its target CPU.
if [[ $# -ne 5 ]]; then
  echo "Usage: $0 <jpackage-app-image> <icon.png> <version> <x64|arm64> <output-dir>" >&2
  exit 2
fi

app_image=$(realpath "$1")
icon=$(realpath "$2")
version=$3
architecture=$4
output=$(realpath -m "$5")
[[ "$version" =~ ^[1-9][0-9]*\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid desktop package version' >&2; exit 2; }
case "$architecture" in
  x64) package_arch=x86_64 ;;
  arm64) package_arch=aarch64 ;;
  *) echo "Unsupported Arch package architecture: $architecture" >&2; exit 2 ;;
esac
[[ -d "$app_image/bin" && -x "$app_image/bin/ForgeLoop Runner" ]] || { echo 'jpackage app image is missing its executable launcher' >&2; exit 1; }
[[ -s "$icon" ]] || { echo 'ForgeLoop icon is missing' >&2; exit 1; }
grep -F -- '-Dforgeloop.desktop.package=pkg.tar.zst' "$app_image/lib/app/ForgeLoop Runner.cfg" >/dev/null || {
  echo 'Arch app image does not target the Arch update package' >&2
  exit 1
}
command -v docker >/dev/null || { echo 'Docker is required to build a pacman package in a clean Arch environment' >&2; exit 1; }

build_root=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/forgeloop-arch.XXXXXX")
trap 'rm -rf -- "$build_root"' EXIT
mkdir -p "$output"
cp "$icon" "$build_root/forgeloop.png"
tar -czf "$build_root/forgeloop-runner-payload.tar.gz" -C "$(dirname "$app_image")" "$(basename "$app_image")"
payload_sha256=$(sha256sum "$build_root/forgeloop-runner-payload.tar.gz" | cut -d ' ' -f1)
icon_sha256=$(sha256sum "$build_root/forgeloop.png" | cut -d ' ' -f1)
cat > "$build_root/forgeloop-runner" <<'WRAPPER'
#!/usr/bin/env sh
set -eu
exec "/opt/forgeloop-runner/ForgeLoop Runner/bin/ForgeLoop Runner" "$@"
WRAPPER
chmod 755 "$build_root/forgeloop-runner"
wrapper_sha256=$(sha256sum "$build_root/forgeloop-runner" | cut -d ' ' -f1)

cat > "$build_root/PKGBUILD" <<PKGBUILD
pkgname=forgeloop-runner
pkgver=$version
pkgrel=1
pkgdesc='ForgeLoop self-hosted software delivery runner'
arch=('$package_arch')
url='https://forgeloop.hookerhillstudios.com'
license=('unknown')
options=('!strip')
depends=('libsecret' 'gtk3' 'libx11' 'libxext' 'libxi' 'libxrender' 'libxtst' 'libxrandr' 'libxinerama' 'libxcursor' 'libxfixes' 'libxcb' 'fontconfig' 'freetype2')
optdepends=('git: clone and inspect repositories' 'docker: execute repository tasks')
source=('forgeloop-runner-payload.tar.gz' 'forgeloop.png' 'forgeloop-runner')
sha256sums=('$payload_sha256' '$icon_sha256' '$wrapper_sha256')

package() {
  install -d "\$pkgdir/opt/forgeloop-runner"
  cp -a "\$srcdir/ForgeLoop Runner/." "\$pkgdir/opt/forgeloop-runner/"
  install -Dm755 "\$srcdir/forgeloop-runner" "\$pkgdir/usr/bin/forgeloop-runner"
  install -Dm644 "\$srcdir/forgeloop.png" "\$pkgdir/usr/share/icons/hicolor/256x256/apps/forgeloop.png"
  install -Dm644 /dev/stdin "\$pkgdir/usr/share/applications/forgeloop-runner.desktop" <<'DESKTOP'
[Desktop Entry]
Name=ForgeLoop Runner
Comment=Run ForgeLoop tasks on infrastructure you control
Exec=forgeloop-runner
Icon=forgeloop
Type=Application
Terminal=false
Categories=Development;Utility;
DESKTOP
}
PKGBUILD

docker run --rm --platform linux/amd64 \
  --env TARGET_ARCH="$package_arch" \
  --volume "$build_root:/build" \
  --workdir /build \
  archlinux:base-devel bash -euc '
    # Pacman's syscall sandbox cannot initialize inside Docker's default seccomp profile.
    pacman -Syu --disable-sandbox --noconfirm
    # makepkg.conf selects the package ABI; override it for the ARM payload
    # because this clean packaging container intentionally runs x64 tooling.
    cp /etc/makepkg.conf /build/makepkg.conf
    printf "\\nCARCH=\\\"%s\\\"\\n" "$TARGET_ARCH" >> /build/makepkg.conf
    useradd --create-home builder
    chown -R builder:builder /build
    su builder -c "cd /build && PKGDEST=/build makepkg --config /build/makepkg.conf --nodeps --noconfirm --cleanbuild"
    package_file=$(find /build -maxdepth 1 -type f -name "forgeloop-runner-*.pkg.tar.zst" -print -quit)
    if [[ -z "$package_file" ]]; then
      echo "makepkg completed without an Arch package; build directory contains:" >&2
      find /build -maxdepth 2 -type f -printf "%p\\n" >&2
      exit 1
    fi
    tar --zstd -xOf "$package_file" .PKGINFO | grep -Fx "arch = $TARGET_ARCH"
    cp "$package_file" /build/forgeloop-runner-output.pkg.tar.zst
    # makepkg runs as builder; restore host traversal after changing the mounted directory owner.
    chmod 755 /build
  '

if [[ ! -s "$build_root/forgeloop-runner-output.pkg.tar.zst" ]]; then
  echo 'makepkg did not create the expected Arch package' >&2
  exit 1
fi
cp "$build_root/forgeloop-runner-output.pkg.tar.zst" \
  "$output/forgeloop-runner-$version-linux-$architecture.pkg.tar.zst"
