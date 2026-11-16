// calendario.mjs — herramientas de Proton Calendar para el servidor MCP «obsidian-nube» (versión 2.4).
//
// Envuelve la CLI no oficial proton-cal (https://github.com/cheeseandcereal/proton-cal, v0.1.3),
// que cifra y descifra los eventos en local con la API privada de Proton.
//
// Uso desde server.mjs (solo en el servidor de Claude, como las herramientas de Proton Drive):
//
//   import { registrarCalendario } from "./calendario.mjs";
//   registrarCalendario(servidor, { registrar: (linea) => log(linea) });
//
// Seguridad:
//   - proton-cal se lanza con execFile (sin shell) y con «--» antes de los argumentos sueltos,
//     así que un título que empiece por «-» no se puede colar como opción.
//   - Las opciones van siempre como --opcion=valor, por la misma razón.
//   - No se exponen las órdenes que tocan calendarios enteros (update/delete calendar).
//   - Borrar una serie entera de eventos que se repiten exige toda_la_serie: true.
//   - Las órdenes que escriben van de una en una (cola) y quedan en el registro.

import { execFile } from "node:child_process";
import { homedir } from "node:os";
import { join } from "node:path";
import { z } from "zod";

const SERVICIOS = join(homedir(), "servicios", "proton-cal");

const POR_DEFECTO = {
  binario: join(SERVICIOS, "proton-cal"),
  carpetaConfig: join(SERVICIOS, "config"),
  zona: "Europe/Madrid",
  tiempoMaximoMs: 90_000,
  prefijo: "calendario_",
  registrar: () => {},
};

// ---------------------------------------------------------------- validación

const FECHA = /^\d{4}-\d{2}-\d{2}$/;
const FECHA_HORA = /^\d{4}-\d{2}-\d{2}( \d{2}:\d{2})?$/;
const ID = /^[A-Za-z0-9_=-]{8,200}$/;
const RECORDATORIO = /^((email|notify):)?\d+[mhdw](\d+[mhdw])*$/;

const zFecha = z.string().regex(FECHA, "Formato AAAA-MM-DD");
const zFechaHora = z
  .string()
  .regex(FECHA_HORA, "Formato «AAAA-MM-DD HH:MM» o, para todo el día, «AAAA-MM-DD»");
const zId = z.string().regex(ID, "Identificador de evento no válido");
const zTexto = (max) => z.string().max(max);
const zCalendario = z
  .string()
  .min(1)
  .max(200)
  .describe("Nombre o ID del calendario. Si no se da, el calendario por defecto de la cuenta.");
const zRecordatorios = z
  .array(z.string().regex(RECORDATORIO, "Formato: 15m, 1h30m, 2d, 1w; con «email:» delante para aviso por correo"))
  .max(10)
  .describe("Recordatorios antes del evento: «15m», «1h», «1d», «email:1h»…");
const zRepetir = z
  .object({
    frecuencia: z.enum(["daily", "weekly", "monthly", "yearly"]).describe("daily, weekly, monthly o yearly"),
    cada: z.number().int().min(1).max(365).optional().describe("Cada cuántos días/semanas/meses/años (por defecto 1)"),
    veces: z.number().int().min(1).max(49).optional().describe("Número de repeticiones (máximo 49)"),
    hasta: zFecha.optional().describe("Último día de la repetición, AAAA-MM-DD"),
  })
  .describe("Hace que el evento se repita.");

// ---------------------------------------------------------------- ejecución

class ErrorCalendario extends Error {}

// Las opciones globales tienen que ir ANTES de «--»: lo que va detrás, proton-cal lo toma como argumentos.
function conOpcionesGlobales(args) {
  const corte = args.indexOf("--");
  const globales = ["--output=json", "--no-color"];
  return corte === -1 ? [...args, ...globales] : [...args.slice(0, corte), ...globales, ...args.slice(corte)];
}

function crearEjecutor(op) {
  let cola = Promise.resolve();

  function lanzar(args) {
    return new Promise((resolver, rechazar) => {
      execFile(
        op.binario,
        conOpcionesGlobales(args),
        {
          env: {
            PATH: process.env.PATH ?? "/usr/bin:/bin",
            HOME: homedir(),
            PROTON_CAL_CONFIG_DIR: op.carpetaConfig,
            TZ: op.zona,
            NO_COLOR: "1",
          },
          timeout: op.tiempoMaximoMs,
          maxBuffer: 16 * 1024 * 1024,
          windowsHide: true,
        },
        (error, salida, errores) => {
          if (!error) {
            try {
              resolver(salida.trim() ? JSON.parse(salida) : null);
            } catch {
              rechazar(new ErrorCalendario("proton-cal no devolvió JSON válido."));
            }
            return;
          }
          rechazar(new ErrorCalendario(explicarError(error, errores)));
        },
      );
    });
  }

  return {
    leer: (args) => lanzar(args),
    // Las escrituras van de una en una: proton-cal ya coordina la renovación de la sesión,
    // pero así nunca hay dos cambios a la vez sobre el mismo evento.
    escribir: (args) => {
      const turno = cola.then(() => lanzar(args));
      cola = turno.catch(() => {});
      return turno;
    },
  };
}

function explicarError(error, errores) {
  if (error.code === "ENOENT") {
    return "No encuentro proton-cal en la Pi. Hay que instalarlo con instalar-proton-cal.sh.";
  }
  if (error.killed || error.signal === "SIGTERM") {
    return "Proton Calendar ha tardado demasiado en responder. Prueba otra vez en un momento.";
  }
  const texto = String(errores || error.message || "")
    .replace(/^Error:\s*/m, "")
    .trim()
    .slice(0, 1500);
  if (/not logged in|proton-cal login|refresh/i.test(texto)) {
    return (
      "La sesión de Proton Calendar en la Pi no está iniciada o ha caducado. " +
      "Nico tiene que lanzar por SSH: ssh -t impresora 'bash servicios/proton-cal/iniciar-sesion.sh'"
    );
  }
  if (/429|rate.?limit|too many/i.test(texto)) {
    return "Proton está limitando las peticiones. Espera un minuto y vuelve a probar.";
  }
  return `proton-cal ha fallado: ${texto || "sin detalle"}`;
}

// ---------------------------------------------------------------- formato

function fechaHoraLocal(ts, zona) {
  // «sv-SE» da justo el formato AAAA-MM-DD HH:MM que espera --occurrence.
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: zona,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  })
    .format(new Date(ts * 1000))
    .replace(",", "");
}

function diaAnterior(fechaIso) {
  const d = new Date(`${fechaIso.slice(0, 10)}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - 1);
  return d.toISOString().slice(0, 10);
}

/** Deja un evento de proton-cal con lo que le sirve a Claude, en español. */
export function resumirEvento(e, zona) {
  const r = { id: e.id, titulo: e.summary || "(sin título)" };
  if (e.all_day) {
    r.todo_el_dia = true;
    r.inicio = e.start?.slice(0, 10);
    // proton-cal da el final exclusivo (como iCal); aquí va el último día, incluido.
    const ultimo = e.end ? diaAnterior(e.end) : r.inicio;
    if (ultimo > r.inicio) r.ultimo_dia = ultimo;
  } else {
    r.inicio = e.start;
    r.fin = e.end;
  }
  if (e.calendar_name) r.calendario = e.calendar_name;
  if (e.location) r.lugar = e.location;
  if (e.description) r.descripcion = e.description;
  if (e.recurring) {
    r.se_repite = e.rrule || true;
    if (e.occurrence_start_ts) {
      // Lo que hay que pasar en «ocurrencia» para editar o borrar solo esta.
      r.ocurrencia = e.all_day
        ? new Date(e.occurrence_start_ts * 1000).toISOString().slice(0, 10)
        : fechaHoraLocal(e.occurrence_start_ts, zona);
    }
  }
  if (e.edited_occurrence) r.ocurrencia_editada = true;
  if (e.organizer?.email) r.organizador = e.organizer.cn || e.organizer.email;
  if (e.attendees?.length) {
    r.invitados = e.attendees.map((a) => ({
      quien: a.cn || a.email,
      ...(a.email && a.cn ? { email: a.email } : {}),
      ...(a.status ? { respuesta: a.status } : {}),
    }));
  }
  if (e.conference?.url) r.videollamada = e.conference.url;
  if (e.notifications?.length) {
    r.recordatorios = e.notifications.map((n) => `${n.type}: ${n.trigger}`);
  }
  return r;
}

const respuesta = (datos) => ({
  content: [{ type: "text", text: typeof datos === "string" ? datos : JSON.stringify(datos, null, 1) }],
});
const fallo = (mensaje) => ({ content: [{ type: "text", text: mensaje }], isError: true });

// ---------------------------------------------------------------- herramientas

/**
 * Registra las herramientas del calendario en un McpServer del SDK de MCP.
 * @param {import("@modelcontextprotocol/sdk/server/mcp.js").McpServer} servidor
 * @param {Partial<typeof POR_DEFECTO> & { origen?: string }} opciones
 */
export function registrarCalendario(servidor, opciones = {}) {
  const op = { ...POR_DEFECTO, ...opciones };
  const proton = crearEjecutor(op);
  const n = (nombre) => op.prefijo + nombre;
  const anotar = (accion, detalle) =>
    op.registrar(`calendario ${accion} ${JSON.stringify(detalle)}${op.origen ? ` (${op.origen})` : ""}`);

  const envolver = (fn) => async (args) => {
    try {
      return await fn(args);
    } catch (e) {
      return fallo(e instanceof ErrorCalendario ? e.message : `Error inesperado: ${e.message}`);
    }
  };

  // --- leer

  servidor.registerTool(
    n("listar_calendarios"),
    {
      title: "Listar calendarios de Proton",
      description: "Lista los calendarios de Proton Calendar de Nico (nombre, ID y cuál es el de por defecto).",
      inputSchema: {},
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    envolver(async () => {
      const lista = await proton.leer(["calendars"]);
      return respuesta(
        (lista ?? []).map((c) => ({ nombre: c.name, id: c.id, por_defecto: c.is_default || undefined })),
      );
    }),
  );

  servidor.registerTool(
    n("ver_agenda"),
    {
      title: "Ver la agenda de Proton",
      description:
        `Lista los eventos de Proton Calendar en un intervalo de días (los que se repiten salen uno a uno). ` +
        `Horas en la zona ${op.zona}. Para editar o borrar solo una repetición, usa su «ocurrencia».`,
      inputSchema: {
        desde: zFechaHora.optional().describe("Inicio: «AAAA-MM-DD» o «AAAA-MM-DD HH:MM». Por defecto, ahora."),
        dias: z.number().int().min(1).max(366).optional().describe("Cuántos días mirar desde «desde» (por defecto 7)."),
        calendarios: z.array(zCalendario).max(20).optional().describe("Solo estos calendarios (nombre o ID)."),
        todos: z.boolean().optional().describe("true para mirar en todos los calendarios."),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    envolver(async ({ desde, dias, calendarios, todos }) => {
      if (todos && calendarios?.length) return fallo("Usa «todos» o «calendarios», no los dos.");
      const args = ["events", `--days=${dias ?? 7}`];
      if (desde) args.push(`--from=${desde}`);
      if (todos) args.push("--all-calendars");
      for (const c of calendarios ?? []) args.push(`--calendar=${c}`);
      const eventos = (await proton.leer(args)) ?? [];
      if (!eventos.length) return respuesta("No hay eventos en ese intervalo.");
      return respuesta(eventos.map((e) => resumirEvento(e, op.zona)));
    }),
  );

  servidor.registerTool(
    n("ver_evento"),
    {
      title: "Ver un evento de Proton",
      description: "Muestra un evento de Proton Calendar con todos sus detalles, a partir de su ID.",
      inputSchema: {
        id: zId.describe("ID del evento (sale en ver_agenda)."),
        calendario: zCalendario.optional(),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    envolver(async ({ id, calendario }) => {
      const args = ["get", "event"];
      if (calendario) args.push(`--calendar=${calendario}`);
      const e = await proton.leer([...args, "--", id]);
      return respuesta(resumirEvento(e, op.zona));
    }),
  );

  // --- escribir

  const opcionesRepetir = (repetir, args) => {
    if (!repetir) return;
    if (repetir.veces && repetir.hasta) throw new ErrorCalendario("En «repetir», usa «veces» o «hasta», no los dos.");
    args.push(`--repeat=${repetir.frecuencia}`);
    if (repetir.cada) args.push(`--every=${repetir.cada}`);
    if (repetir.veces) args.push(`--count=${repetir.veces}`);
    if (repetir.hasta) args.push(`--until=${repetir.hasta}`);
  };

  servidor.registerTool(
    n("crear_evento"),
    {
      title: "Crear un evento en Proton",
      description:
        `Crea un evento en Proton Calendar. Horas en la zona ${op.zona} salvo que se indique otra. ` +
        `Para todo el día: todo_el_dia true e inicio/fin como «AAAA-MM-DD» (el fin es el último día, incluido).`,
      inputSchema: {
        titulo: zTexto(300).min(1),
        inicio: zFechaHora.describe("«AAAA-MM-DD HH:MM» (o «AAAA-MM-DD» si es de todo el día)."),
        fin: zFechaHora
          .optional()
          .describe("Igual que inicio. Si falta: duración por defecto del calendario, o un solo día."),
        todo_el_dia: z.boolean().optional(),
        descripcion: zTexto(10_000).optional(),
        lugar: zTexto(500).optional(),
        calendario: zCalendario.optional(),
        zona_horaria: z.string().max(64).optional().describe(`Zona IANA, p. ej. «Europe/London». Por defecto ${op.zona}.`),
        repetir: zRepetir.optional(),
        recordatorios: zRecordatorios.optional(),
        sin_recordatorios: z.boolean().optional().describe("true para que no tenga ningún recordatorio."),
      },
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: true },
    },
    envolver(async (a) => {
      if (a.recordatorios?.length && a.sin_recordatorios) {
        return fallo("Usa «recordatorios» o «sin_recordatorios», no los dos.");
      }
      const args = ["create", "event", `--start=${a.inicio}`];
      if (a.fin) args.push(`--end=${a.fin}`);
      if (a.todo_el_dia) args.push("--all-day");
      if (a.descripcion) args.push(`--description=${a.descripcion}`);
      if (a.lugar) args.push(`--location=${a.lugar}`);
      if (a.calendario) args.push(`--calendar=${a.calendario}`);
      if (a.zona_horaria) args.push(`--tz=${a.zona_horaria}`);
      opcionesRepetir(a.repetir, args);
      for (const r of a.recordatorios ?? []) args.push(`--reminder=${r}`);
      if (a.sin_recordatorios) args.push("--no-reminders");
      const creado = await proton.escribir([...args, "--", a.titulo]);
      anotar("crear", { id: creado?.id, titulo: a.titulo, inicio: a.inicio });
      return respuesta({ creado: true, id: creado?.id, titulo: a.titulo, inicio: a.inicio, se_repite: creado?.rrule });
    }),
  );

  servidor.registerTool(
    n("editar_evento"),
    {
      title: "Editar un evento de Proton",
      description:
        "Cambia un evento de Proton Calendar. Solo cambia lo que se pase; una cadena vacía en " +
        "descripcion o lugar los borra. En un evento que se repite, sin «ocurrencia» cambia toda la serie; " +
        "con «ocurrencia» (sale en ver_agenda) cambia solo esa repetición.",
      inputSchema: {
        id: zId,
        ocurrencia: zFechaHora.optional().describe("Inicio original de la repetición que se quiere cambiar."),
        titulo: zTexto(300).min(1).optional(),
        inicio: zFechaHora.optional(),
        fin: zFechaHora.optional(),
        descripcion: zTexto(10_000).optional(),
        lugar: zTexto(500).optional(),
        calendario: zCalendario.optional().describe("Calendario donde está el evento (no lo mueve de calendario)."),
        zona_horaria: z.string().max(64).optional(),
        repetir: zRepetir.optional().describe("Cambia la repetición (borra las repeticiones editadas a mano)."),
        dejar_de_repetir: z.boolean().optional(),
        recordatorios: zRecordatorios.optional().describe("Sustituye los recordatorios por estos."),
        sin_recordatorios: z.boolean().optional(),
        recordatorios_por_defecto: z.boolean().optional().describe("Vuelve a los recordatorios del calendario."),
      },
      annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: true },
    },
    envolver(async (a) => {
      const modos = [a.recordatorios?.length > 0, a.sin_recordatorios, a.recordatorios_por_defecto].filter(Boolean);
      if (modos.length > 1) return fallo("Elige solo una forma de cambiar los recordatorios.");
      if (a.repetir && a.dejar_de_repetir) return fallo("Usa «repetir» o «dejar_de_repetir», no los dos.");
      const args = ["update", "event"];
      if (a.ocurrencia) args.push(`--occurrence=${a.ocurrencia}`);
      if (a.calendario) args.push(`--calendar=${a.calendario}`);
      if (a.zona_horaria) args.push(`--tz=${a.zona_horaria}`);
      const sinCambios = args.length;
      if (a.titulo !== undefined) args.push(`--summary=${a.titulo}`);
      if (a.inicio) args.push(`--start=${a.inicio}`);
      if (a.fin) args.push(`--end=${a.fin}`);
      if (a.descripcion !== undefined) args.push(`--description=${a.descripcion}`);
      if (a.lugar !== undefined) args.push(`--location=${a.lugar}`);
      opcionesRepetir(a.repetir, args);
      if (a.dejar_de_repetir) args.push("--no-repeat");
      for (const r of a.recordatorios ?? []) args.push(`--reminder=${r}`);
      if (a.sin_recordatorios) args.push("--no-reminders");
      if (a.recordatorios_por_defecto) args.push("--reminders-default");
      if (args.length === sinCambios) return fallo("No has pedido ningún cambio.");
      const r = await proton.escribir([...args, "--", a.id]);
      anotar("editar", { id: a.id, ocurrencia: a.ocurrencia });
      return respuesta({
        editado: r?.updated ?? true,
        solo_esa_repeticion: r?.edited_occurrence || undefined,
        repeticiones_editadas_que_se_pierden: r?.removed_exceptions || undefined,
      });
    }),
  );

  servidor.registerTool(
    n("borrar_evento"),
    {
      title: "Borrar un evento de Proton",
      description:
        "Borra un evento de Proton Calendar. Si se repite, pasa «ocurrencia» para borrar solo esa repetición; " +
        "para borrar la serie entera hace falta toda_la_serie: true. Antes de borrar, confirma con Nico qué evento es.",
      inputSchema: {
        id: zId,
        ocurrencia: zFechaHora.optional().describe("Inicio original de la repetición que se quiere borrar."),
        toda_la_serie: z.boolean().optional().describe("true para borrar todas las repeticiones."),
        calendario: zCalendario.optional(),
      },
      annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: true },
    },
    envolver(async ({ id, ocurrencia, toda_la_serie, calendario }) => {
      if (ocurrencia && toda_la_serie) return fallo("Usa «ocurrencia» o «toda_la_serie», no los dos.");
      const cal = calendario ? [`--calendar=${calendario}`] : [];
      if (!ocurrencia && !toda_la_serie) {
        const e = await proton.leer(["get", "event", ...cal, "--", id]);
        if (e?.recurring && !e?.edited_occurrence) {
          return fallo(
            `«${e.summary}» se repite. Pasa «ocurrencia» para borrar solo una repetición ` +
              "o toda_la_serie: true para borrarlas todas.",
          );
        }
      }
      const args = ["delete", "event", ...cal];
      if (ocurrencia) args.push(`--occurrence=${ocurrencia}`);
      await proton.escribir([...args, "--", id]);
      anotar("borrar", { id, ocurrencia, toda_la_serie });
      return respuesta({ borrado: true, id, ocurrencia });
    }),
  );
}
