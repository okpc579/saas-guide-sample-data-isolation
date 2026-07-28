#!/bin/sh
set -eu
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
MVN_VERSION=3.9.10
BASE="${HOME}/.m2/wrapper/dists/apache-maven-${MVN_VERSION}"
if [ ! -x "$BASE/bin/mvn" ]; then
  mkdir -p "$(dirname "$BASE")"
  ARCHIVE="${TMPDIR:-/tmp}/apache-maven-${MVN_VERSION}-bin.zip"
  curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MVN_VERSION}/apache-maven-${MVN_VERSION}-bin.zip" -o "$ARCHIVE"
  unzip -q "$ARCHIVE" -d "$(dirname "$BASE")"
fi
exec "$BASE/bin/mvn" "$@"
