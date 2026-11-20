#!/bin/sh
# Fetches PlantUML from Maven Central into this folder (git ignores it), checked against its published SHA-1.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
version=1.2024.7
sha1=cb57b315d96413d55622edaa8c4b234e2ebf4b1f
url="https://repo1.maven.org/maven2/net/sourceforge/plantuml/plantuml/$version/plantuml-$version.jar"
curl -sfL "$url" -o "$here/plantuml.jar.part"
echo "$sha1  $here/plantuml.jar.part" | shasum -c - >/dev/null
mv "$here/plantuml.jar.part" "$here/plantuml.jar"
echo "plantuml $version at $here/plantuml.jar"
