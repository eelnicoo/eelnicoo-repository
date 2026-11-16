# Proton Calendar en `obsidian-nube` (versión 2.4)

Herramientas para que Claude lea y cambie tu Proton Calendar desde el chat (móvil, tablet y web) y desde Claude Code. Van dentro del servidor MCP `obsidian-nube` de la Pi (`~/servicios/obsidian-mcp/server.mjs`).

Por debajo usan [proton-cal](https://github.com/cheeseandcereal/proton-cal) v0.1.3, una CLI no oficial en Go (licencia Unlicense). Cifra y descifra los eventos en la propia Pi con la API privada de Proton. Hay binario estático para `linux-arm64` y funciona con el kernel de 64 bits de la Pi sin instalar nada más.

## Herramientas

| Herramienta | Qué hace |
|---|---|
| `calendario_listar_calendarios` | Nombres e IDs de tus calendarios |
| `calendario_ver_agenda` | Eventos de un intervalo (`desde`, `dias`, `calendarios` o `todos`) |
| `calendario_ver_evento` | Un evento con todos sus detalles |
| `calendario_crear_evento` | Crea un evento: con hora o de todo el día, que se repite, con recordatorios |
| `calendario_editar_evento` | Cambia solo lo que se pase; con `ocurrencia`, solo esa repetición |
| `calendario_borrar_evento` | Borra un evento; una serie entera solo con `toda_la_serie: true` |

Medidas de seguridad:

- proton-cal se lanza con `execFile` (sin shell) y con un entorno limpio.
- Los textos van como `--opcion=valor`, y el título y el ID van detrás de `--`. Un título como `--help` no se puede colar como opción.
- Los IDs se validan antes de llamar a proton-cal.
- No hay herramientas para cambiar ni borrar calendarios enteros.
- Los cambios van de uno en uno y quedan en `servidor.log`.

## 1. Instalar en la Pi

```bash
# Desde el PC
scp -r proton-calendar impresora:servicios/obsidian-mcp/calendario-nuevo
ssh impresora
mkdir -p ~/servicios/proton-cal
cp ~/servicios/obsidian-mcp/calendario-nuevo/{instalar-proton-cal.sh,iniciar-sesion.sh} ~/servicios/proton-cal/
bash ~/servicios/proton-cal/instalar-proton-cal.sh
exit
```

El script baja el binario y lo comprueba contra `checksums.txt` y contra la huella apuntada en el propio script. Si algo no coincide, no instala nada.

## 2. Iniciar sesión (una vez, y cuando caduque)

```bash
ssh -t impresora 'bash servicios/proton-cal/iniciar-sesion.sh'
```

- Te pide usuario, contraseña y el código 2FA (TOTP). Las llaves FIDO2 no valen.
- Si Proton pide CAPTCHA, te da un enlace: ábrelo en el PC, resuélvelo y pulsa Intro.
- Al acabar, te enseña tus calendarios y los próximos 3 días.
- La contraseña no se guarda: solo los tokens, en `~/servicios/proton-cal/config/session.json` (permisos 600). **Nunca va al vault.**
- Si la sesión caduca, las herramientas responden «La sesión de Proton Calendar en la Pi no está iniciada o ha caducado», con esta misma orden.

> ⚠️ **Va con tu cuenta principal (lo elegiste así).** `session.json` es una sesión de Proton completa. Quien entrara en la Pi como `pi` tendría tu calendario y, con algo de trabajo, el resto de la cuenta. Es el mismo riesgo que ya aceptaste con la sesión de Drive (`pd`). Para cortarla: `proton-cal logout` o cerrar la sesión en Proton → Seguridad → Sesiones.

## 3. Engancharlo en `server.mjs`

Antes, copia la versión que funciona: `cp server.mjs server-2.3.mjs.bak`.

```bash
cd ~/servicios/obsidian-mcp
cp calendario-nuevo/calendario.mjs .
node -e "import('zod').then(()=>console.log('zod ok'))"   # viene con el SDK de MCP
```

En `server.mjs`, donde se registran las herramientas de Proton Drive **solo para Claude** (las que ChatGPT no ve):

```js
import { registrarCalendario } from "./calendario.mjs";

// … dentro del bloque de las herramientas solo para Claude:
registrarCalendario(servidor, {
  registrar: (linea) => anotarEnLog(linea),   // la función que ya escribe en servidor.log
  origen: "claude",
});
```

Opciones, todas con valor por defecto:

- `binario`: `~/servicios/proton-cal/proton-cal`;
- `carpetaConfig`: `~/servicios/proton-cal/config`;
- `zona`: `Europe/Madrid`;
- `tiempoMaximoMs`: `90000`;
- `prefijo`: `calendario_`.

### Ruta aparte para Claude Code

En Claude Code los conectores de claude.ai están desactivados. Ahí conviene un servidor que **solo** tenga el calendario, en `/calendario/mcp`, con la misma cabecera `Authorization: Bearer`. En el manejador HTTP, igual que con `/mcp`, pero con un `McpServer` nuevo que solo tenga esto:

```js
const soloCalendario = new McpServer({ name: "proton-calendario", version: "2.4.0" });
registrarCalendario(soloCalendario, { registrar: anotarEnLog, origen: "claude-code" });
```

Luego, en cada PC (`--scope user` para que valga en todas las carpetas):

```bash
claude mcp add --scope user --transport http proton-calendario \
  https://impresora.tail6e2bd8.ts.net/calendario/mcp \
  --header "Authorization: Bearer <secreto de secreto.txt>"
```

## 4. Probar antes de pasarlo a producción

Como con las versiones anteriores, primero en la instancia de prueba (puerto 8766):

```bash
cd ~/servicios/obsidian-mcp/calendario-nuevo
npm install                     # solo para las pruebas
npm test                        # 14 pruebas con un proton-cal falso: no toca Proton
node pruebas/probar.mjs --real ~/servicios/proton-cal/proton-cal
```

La prueba `--real` hay que lanzarla **antes** de iniciar sesión, o con `PROTON_CAL_CONFIG_DIR` apuntando a una carpeta vacía. Comprueba que el binario de verdad acepta todas las opciones que le pasa el módulo: cada llamada tiene que fallar solo por «no hay sesión».

Después, con sesión y desde el chat:

1. «¿Qué tengo esta semana?»
2. «Crea un evento de prueba mañana a las 10 que se llame Prueba Claude»
3. Comprueba en la app que aparece.
4. «Muévelo a las 11»
5. «Bórralo»

Para relanzar el servidor, el truco de siempre: matar el PID y dejar que `cron` lo levante, o `ssh -n impresora 'setsid -f bash …/arrancar.sh > /dev/null 2>&1 < /dev/null'`.

## Límites conocidos

- **No es oficial.** Si Proton cambia su API privada, puede dejar de funcionar hasta que proton-cal saque versión. Fija la versión en el script y actualízala a mano.
- No envía invitaciones ni gestiona asistentes (los muestra, pero no los cambia).
- No crea calendarios ni hace «esta y las siguientes» en eventos que se repiten.
- Al cambiar la repetición o las horas de una serie, se pierden las repeticiones editadas a mano. La respuesta lo dice en `repeticiones_editadas_que_se_pierden`.
- Repetir: máximo 49 veces con `veces`; para más, `hasta`.
