#!/bin/bash
# Fast local type-checks. No APK, no linking, no device.
#
# WHY
#   Building the APK cross-compiles Rust with the NDK and happens in GitHub Actions only
#   (R4 / D14). But *type-checking* needs none of that, and a CI round-trip to discover a
#   typo costs ~30 minutes. These checks cost ~95 seconds together.
#
#   What this does NOT do: link, produce .so files, build an APK, or prove anything runs.
#   Cross-device sync can only be verified on two real phones (roadmap 1.5).
#
# COVERAGE — read this before trusting a green run
#   ✅ All Kotlin, including SyncInterface.kt and WebUIFragment.kt
#   ✅ aw-sync/src/sync_wrapper.rs, dirs.rs, util.rs, sync.rs
#   ❌ aw-sync/src/android.rs  — see "THE ANDROID GAP" below
#
# THE ANDROID GAP
#   android.rs is behind `#[cfg(target_os = "android")]` (aw-sync/src/lib.rs), so the host
#   check skips it silently. Checking it needs --target aarch64-linux-android, and that is
#   NOT currently possible on this Windows machine:
#
#     aw-server/Cargo.toml pulls openssl-sys with `vendored` for the android target, so the
#     check must build OpenSSL 3.5.1 from source. That needs a Unix-shaped perl:
#       - Strawberry perl has the modules but Configure REJECTS it ("doesn't produce Unix
#         like paths").
#       - Git Bash's perl produces Unix paths but is a stripped build missing core modules.
#     Feeding Git Bash's perl the missing pure-perl modules (Locale::Maketext, ExtUtils,
#     Pod, Text — copied from Strawberry into C:/dev/tools/perl-overlay, with
#     MSYS2_ENV_CONV_EXCL=PERL5LIB so the path is not mangled) gets Configure to pass
#     completely. `make` then dies on OpenSSL's $(CROSS_COMPILE)$(CC) joining without a
#     separator: ".../windows-x86_64/bin" + "clang.exe" -> "binclang.exe".
#
#   Attempted 2026-09-02 and stopped there deliberately. mobile/build.gradle carries the
#   maintainers' own note on this: "Doesn't work, chokes on building openssl-sys."
#   **android.rs is covered by CI, which has to run before device testing anyway.**
#
# REQUIREMENTS (all present on this machine as of 2026-09-02)
#   Android Studio JBR (JDK 21), Android SDK w/ platform 36, Rust stable, MSVC 14.44.
#   No NDK needed for these checks.
#
# USAGE
#   scripts/check-local.sh            # both      (~95s)
#   scripts/check-local.sh kotlin     # Kotlin    (~70s)
#   scripts/check-local.sh rust       # Rust host (~25s)
#   scripts/check-local.sh syntax     # parse-check cfg-gated files, incl. android.rs (~1s)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

check_kotlin() {
    echo "=== Kotlin ==============================================="
    # cargoBuild is deliberately not wired into the Gradle build (mobile/build.gradle), so
    # this compiles Kotlin without needing any .so files to exist.
    cd "$REPO_ROOT"
    JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}" \
        ./gradlew --no-daemon :mobile:compileDebugKotlin
}



check_jni() {
    echo "=== JNI symbol cross-check ================================"
    # Every `external fun NAME` in Kotlin needs a Rust export literally called
    # Java_<pkg>_<Class>_NAME. Nothing else catches a mismatch: the Rust compiles, the .so
    # loads, and the symbol is simply absent under the name the JVM looks up -- it surfaces
    # as UnsatisfiedLinkError at runtime, on a device.
    #
    # Added 2026-09-02 after awSyncInitLogging was exported as a plain C symbol
    # (aw_sync_init_logging). SyncInterface's constructor threw, SyncScheduler disabled
    # itself, and NO sync ran at all -- for two days, looking like a sync bug.
    local rc=0
    _cmp() { # <label> <kotlin file> <rust file> <jni class prefix>
        local label="$1" kt="$2" rs="$3" prefix="$4"
        local missing
        missing=$(comm -23 \
            <(grep -oP 'external fun \K\w+' "$kt" | sort -u) \
            <(grep -oP "${prefix}\K\w+" "$rs" | sort -u))
        if [ -z "$missing" ]; then
            printf '  %-16s ok\n' "$label"
        else
            printf '  %-16s MISSING Rust export(s):\n' "$label"
            echo "$missing" | sed 's/^/      /'
            rc=1
        fi
    }
    _cmp "SyncInterface" \
        "$REPO_ROOT/mobile/src/main/java/net/activitywatch/android/SyncInterface.kt" \
        "$REPO_ROOT/aw-server-rust/aw-sync/src/android.rs" \
        'Java_net_activitywatch_android_SyncInterface_'
    _cmp "RustInterface" \
        "$REPO_ROOT/mobile/src/main/java/net/activitywatch/android/RustInterface.kt" \
        "$REPO_ROOT/aw-server-rust/aw-server/src/android/mod.rs" \
        'Java_net_activitywatch_android_RustInterface_'
    return $rc
}
check_syntax() {
    echo "=== Rust syntax (cfg-gated files) ========================="
    # rustfmt parses a file regardless of #[cfg], so it reaches android.rs where
    # `cargo check` cannot. It catches SYNTAX errors only -- not type errors, not wrong
    # JNI signatures. Added 2026-09-02 after a stray `}` in android.rs, introduced by a
    # sed edit, cost a full ~25min CI cycle. rustfmt reproduces CI's exact message in <1s.
    export PATH="$HOME/.cargo/bin:$PATH"
    cd "$REPO_ROOT/aw-server-rust"
    local rc=0
    for f in aw-sync/src/android.rs aw-sync/src/sync_wrapper.rs aw-sync/src/dirs.rs aw-sync/src/util.rs; do
        printf '  %-30s ' "$f"
        if rustfmt --edition 2021 --emit stdout "$f" >/dev/null 2>/tmp/aw-rustfmt.err; then
            echo "ok"
        else
            echo "PARSE ERROR"; sed -n '1,4p' /tmp/aw-rustfmt.err; rc=1
        fi
    done
    return $rc
}
check_rust() {
    echo "=== Rust (host target) ==================================="
    echo "NOTE: does not cover android.rs — see THE ANDROID GAP in this file."
    export PATH="$HOME/.cargo/bin:$PATH"
    cd "$REPO_ROOT/aw-server-rust"
    cargo check -p aw-sync --lib
}

case "${1:-all}" in
    kotlin) check_kotlin ;;
    rust)   check_rust ;;
    syntax) check_syntax ;;
    jni)    check_jni ;;
    all)    check_syntax; check_jni; check_kotlin; check_rust ;;
    *)      echo "usage: $0 [all|kotlin|rust|syntax]" >&2; exit 2 ;;
esac

echo
echo "Type-checks passed. This does NOT mean it works, and android.rs was not checked."
echo "Build in CI, then verify on two devices (roadmap 1.5)."
