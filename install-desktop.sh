#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Building and installing CloudStream Desktop for Linux..."
./gradlew :desktop-app:installLinuxApp "$@"
