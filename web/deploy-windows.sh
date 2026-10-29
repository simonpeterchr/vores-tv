#!/bin/sh
# Kopierer webappen til en Windows-maskine (mappen C:\VoresTV, planlagt opgave "VoresTV") og genstarter den.
# Brug: ./deploy-windows.sh bruger@ip    – eller sæt VORESTV_HOST og evt. VORESTV_KEY (SSH-nøgle) i deploy.local
set -e
cd "$(dirname "$0")"
[ -f deploy.local ] && . ./deploy.local
HOST="${1:-$VORESTV_HOST}"
[ -n "$HOST" ] || { echo "Brug: $0 bruger@ip (eller VORESTV_HOST i deploy.local)"; exit 1; }
w() { ssh ${VORESTV_KEY:+-i "$VORESTV_KEY"} "$HOST" "$@"; }
COPYFILE_DISABLE=1 tar -cf - server.js lib public | w "tar -xf - -C C:/VoresTV"
w "schtasks /end /tn VoresTV >nul & schtasks /run /tn VoresTV"
echo "Opdateret på $HOST"
