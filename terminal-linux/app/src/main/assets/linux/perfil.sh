# Terminal Linux: ajustes para Ubuntu dentro de Android (/etc/profile.d).
# La app reescribe este archivo cada vez que se abre.

# libuv (Node.js) no puede usar io_uring dentro de proot.
export UV_USE_IO_URING=0
# Los programas que abren el navegador usan «abrir» (lo abre en Android).
export BROWSER=abrir
# Aquí instala Claude Code su comando «claude».
case ":$PATH:" in
*":$HOME/.local/bin:"*) ;;
*) export PATH="$HOME/.local/bin:$PATH" ;;
esac

if [ -n "${PS1:-}" ] && [ ! -f "$HOME/.hushlogin" ]; then
    printf '\033[1;36mTerminal Linux\033[0m · Ubuntu 24.04 dentro de Android\n'
    printf '  \033[1msudo apt update\033[0m · \033[1msudo apt install <paquete>\033[0m\n'
    if command -v claude >/dev/null 2>&1; then
        printf '  \033[1mclaude\033[0m             abre Claude Code\n'
    else
        printf '  \033[1minstalar-claude\033[0m    instala Claude Code\n'
    fi
    printf '  \033[1m~/tablet\033[0m           archivos de la tablet (/sdcard)\n'
    printf '  \033[1mabrir <archivo|url>\033[0m lo abre con las apps de Android\n'
    printf '  (para no ver este mensaje: touch ~/.hushlogin)\n\n'
fi
