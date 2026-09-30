#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <package.pkg.tar.zst> <x64|arm64>" >&2
  exit 2
fi
package=$(realpath "$1")
architecture=$2
case "$architecture" in
  x64) package_arch=x86_64 ;;
  arm64) package_arch=aarch64 ;;
  *) echo "Unsupported Arch package architecture: $architecture" >&2; exit 2 ;;
esac
[[ -s "$package" ]] || { echo 'Arch package is missing' >&2; exit 1; }
command -v docker >/dev/null || { echo 'Docker is required for the Arch package smoke test' >&2; exit 1; }

# Test package metadata for both CPUs. On x64, also install it with pacman and
# exercise the installed launcher; ARM payload execution is covered by the
# native ARM runner build job, while this container validates its package ABI.
docker run --rm --platform linux/amd64 \
  --env TARGET_ARCH="$package_arch" \
  --env INSTALL_PACKAGE="$([[ "$architecture" == x64 ]] && echo true || echo false)" \
  --volume "$package:/tmp/forgeloop-runner.pkg.tar.zst:ro" \
  archlinux:base bash -euc '
    pacman -Syu --noconfirm
    tar --zstd -xOf /tmp/forgeloop-runner.pkg.tar.zst .PKGINFO | grep -Fx "arch = $TARGET_ARCH"
    tar --zstd -tf /tmp/forgeloop-runner.pkg.tar.zst | grep -Fx "usr/bin/forgeloop-runner"
    tar --zstd -tf /tmp/forgeloop-runner.pkg.tar.zst | grep -Fx "usr/share/applications/forgeloop-runner.desktop"
    if [[ "$INSTALL_PACKAGE" == true ]]; then
      pacman -S --noconfirm --needed libsecret gtk3 libx11 libxext libxi libxrender libxtst libxrandr libxinerama libxcursor libxfixes libxcb fontconfig freetype2
      pacman -U --noconfirm /tmp/forgeloop-runner.pkg.tar.zst
      /usr/bin/forgeloop-runner --version
      pacman -Rns --noconfirm forgeloop-runner
    fi
  '
