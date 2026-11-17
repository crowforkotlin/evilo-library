#!/usr/bin/env bash
# Sets the four Maven Central secrets on the evilo-library repository.
#
# `gh secret set NAME` with no --body opens an interactive prompt that reads the
# value from the terminal. Putting that in a `for` loop makes every iteration
# fight for the same stdin, so the values arrive in the wrong secrets or not at
# all. This script reads each value once with echo off and passes it with
# --body, so nothing depends on prompt plumbing.
#
# Usage:
#   ./.github/scripts/setup_central_secrets.sh
#   ./.github/scripts/setup_central_secrets.sh --from-key 0B4189D9794C625F
#
# The second form exports the signing key from your local GnuPG keyring by id,
# so that secret never has to pass through a paste buffer.
set -euo pipefail

REPO="${EVILO_SECRET_REPO:-crowforkotlin/evilo-library}"
KEY_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from-key) KEY_ID="${2:?--from-key needs a key id}"; shift 2 ;;
        --repo) REPO="${2:?--repo needs owner/name}"; shift 2 ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

# Reads one value without echoing it. Arms read -s only when stdin is a terminal,
# so the script still works when driven from a pipe.
read_secret() {
    local prompt="$1" value
    if [[ -t 0 ]]; then
        read -r -s -p "$prompt" value
        echo
    else
        read -r value
    fi
    [[ -n "$value" ]] || { echo "Empty value; aborting." >&2; exit 1; }
    printf '%s' "$value"
}

set_secret() {
    local name="$1" value="$2"
    printf '%s' "$value" | gh secret set "$name" --repo "$REPO" >/dev/null
    echo "  set $name"
}

echo "Repository: $REPO"
echo

echo "Maven Central user token (Portal -> Account -> Generate User Token)."
echo "It is a username plus a password; both come from that one token."
set_secret MAVEN_CENTRAL_USERNAME "$(read_secret '  username: ')"
set_secret MAVEN_CENTRAL_PASSWORD "$(read_secret '  password: ')"

echo
if [[ -n "$KEY_ID" ]]; then
    echo "Exporting the signing key $KEY_ID from your local GnuPG keyring."
    armor=$(GNUPGHOME="${GNUPGHOME:-$HOME/.gnupg}" gpg --armor --export-secret-keys "$KEY_ID")
    [[ -n "$armor" ]] || { echo "gpg produced no key material for $KEY_ID." >&2; exit 1; }
    set_secret MAVEN_CENTRAL_SIGNING_KEY "$armor"
    echo "GnuPG may have asked for your key passphrase above."
    set_secret MAVEN_CENTRAL_SIGNING_KEY_PASSWORD "$(read_secret '  key passphrase: ')"
else
    echo "ASCII-armored private key (gpg --armor --export-secret-keys <id>)."
    set_secret MAVEN_CENTRAL_SIGNING_KEY "$(read_secret '  armored key: ')"
    set_secret MAVEN_CENTRAL_SIGNING_KEY_PASSWORD "$(read_secret '  key passphrase: ')"
fi

echo
echo "Stored. Verifying the workflow can see them:"
gh secret list --repo "$REPO"
