#!/usr/bin/env bash
# Inicia (o renueva) la sesión de Proton Calendar que usa el conector obsidian-nube.
# Lánzalo por SSH desde el PC, con terminal:
#   ssh -t impresora 'bash servicios/proton-cal/iniciar-sesion.sh'
# Pide usuario, contraseña y el código 2FA (TOTP). Si Proton pide CAPTCHA, imprime un
# enlace: ábrelo en el navegador del PC, resuélvelo y pulsa Intro aquí.
# La contraseña no se guarda: solo los tokens de sesión, en config/session.json (permisos 600).
set -euo pipefail

DIR="$HOME/servicios/proton-cal"
export PROTON_CAL_CONFIG_DIR="$DIR/config"
export TZ="${TZ:-Europe/Madrid}"

"$DIR/proton-cal" login
chmod 600 "$PROTON_CAL_CONFIG_DIR"/*.json "$PROTON_CAL_CONFIG_DIR"/*.toml 2>/dev/null || true

echo
echo "Calendarios de la cuenta:"
"$DIR/proton-cal" calendars
echo
echo "Próximos 3 días:"
"$DIR/proton-cal" events --days 3
