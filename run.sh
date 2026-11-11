#!/usr/bin/env bash
set -euo pipefail

# Execute from the script's directory.
cd "$(dirname "$0")"

# Stop containers and delete volumes to reset the database.
docker compose down -v

# Rebuild and run the app, removing its container afterward.
docker compose run --build --rm app