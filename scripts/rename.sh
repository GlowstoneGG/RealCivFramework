#!/usr/bin/env bash
# Rebrands a fresh copy of the Real Civ Framework template: plugin name, Kotlin package, Gradle group,
# permission nodes and the storage namespace. Run once, before your server has player data.
#
#   scripts/rename.sh MyCiv com.example.myciv
set -euo pipefail

name=${1:-}
package=${2:-}
if [[ ! $name =~ ^[A-Z][A-Za-z0-9]*$ || ! $package =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
    echo "usage: scripts/rename.sh <Name> <package>   e.g. scripts/rename.sh MyCiv com.example.myciv" >&2
    echo "  Name: letters and digits, starting with a capital (becomes the plugin and class name)" >&2
    echo "  package: a lowercase Java package" >&2
    exit 1
fi
id=$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')

cd "$(git rev-parse --show-toplevel)"
if [[ ! -d core/src/main/kotlin/gg/glowstone/rcf ]]; then
    echo "This copy has already been renamed." >&2
    exit 1
fi
if [[ -n $(git status --porcelain) ]]; then
    echo "Commit or stash your changes first, so the rename is easy to review and undo." >&2
    exit 1
fi

old_path=gg/glowstone/rcf
new_path=${package//.//}
for source_set in core/src/main core/src/test folia/src/main folia/src/test; do
    [[ -d $source_set/kotlin/$old_path ]] || continue
    mkdir -p "$source_set/kotlin/$(dirname "$new_path")"
    git mv "$source_set/kotlin/$old_path" "$source_set/kotlin/$new_path"
    # Drop the now-empty gg/glowstone parents.
    find "$source_set/kotlin/gg" -type d -empty -delete 2>/dev/null || true
done
git mv "folia/src/main/kotlin/$new_path/folia/RealCivFrameworkPlugin.kt" "folia/src/main/kotlin/$new_path/folia/${name}Plugin.kt"

# Package first, then the remaining mentions: class names, plugin name, permissions, namespace.
files=$(git ls-files '*.kt' '*.kts' '*.yml' '*.md' | grep -v '^LICENSE')
NAME=$name ID=$id PKG=$package PKG_PATH=$new_path perl -pi -e '
    s/gg\.glowstone\.rcf/$ENV{PKG}/g;
    s{gg/glowstone/rcf}{$ENV{PKG_PATH}}g;
    s/Real Civ Framework/$ENV{NAME}/g;
    s/RealCivFramework/$ENV{NAME}/g;
    s/\brcf\b/$ENV{ID}/g;
' $files

# Upstream-specific plugin.yml metadata; add your own.
perl -ni -e 'print unless /^(authors|website):/' folia/src/main/resources/plugin.yml

# The template walkthrough only applies before renaming.
perl -0pi -e 's/<!-- template:start -->.*?<!-- template:end -->\n*//s' README.md
cat >> README.md <<'EOF'

Based on [Real Civ Framework](https://github.com/GlowstoneGG/RealCivFramework), licensed GPL-3.0.
EOF

git rm -q scripts/rename.sh

echo "Renamed to $name ($package). Storage namespace and permission prefix: $id"
echo "Next: review 'git diff --stat', run ./gradlew build, then edit README.md and add authors/website to plugin.yml."
