#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$script_dir/server"

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    echo "ERROR: Node.js and npm are required. Install Node.js LTS from https://nodejs.org/" >&2
    echo "Then reopen this terminal and run this script again." >&2
    exit 1
fi

echo "Installing server dependencies from package-lock.json..."
if npm ci; then
    echo "Starting server. Keep this terminal open. Press Ctrl+C to stop."
    exec npm start
else
    install_exit=$?
    echo "ERROR: Dependency installation failed. Server was not started." >&2
    echo "Check the npm error above and your network connection." >&2
    exit "$install_exit"
fi
