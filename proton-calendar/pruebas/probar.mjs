// Pruebas de calendario.mjs con un cliente MCP en memoria.
//
//   node pruebas/probar.mjs                 → contra proton-cal-falso.mjs (no toca Proton)
//   node pruebas/probar.mjs --real <binario> → contra el binario real SIN sesión: comprueba que
//                                              acepta todas las opciones (debe responder «no hay sesión»)
//
// Necesita @modelcontextprotocol/sdk y zod (los del servidor obsidian-mcp).
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync, chmodSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { registrarCalendario, resumirEvento } from "../calendario.mjs";

const aqui = dirname(fileURLToPath(import.meta.url));
const tmp = mkdtempSync(join(tmpdir(), "prueba-calendario-"));
const real = process.argv[2] === "--real" ? process.argv[3] : null;

let binario = real;
const registroFalso = join(tmp, "args.log");
if (!real) {
  binario = join(tmp, "proton-cal");
  // calendario.mjs le pasa a proton-cal un entorno limpio, así que las variables van en el lanzador.
  writeFileSync(
    binario,
    `#!/bin/sh\nexport FALSO_REGISTRO="${registroFalso}" FALSO_MARCA="${join(tmp, "fallo-sesion")}"\n` +
      `exec "${process.execPath}" "${join(aqui, "proton-cal-falso.mjs")}" "$@"\n`,
  );
  chmodSync(binario, 0o755);
  writeFileSync(registroFalso, "");
}

const registro = [];
async function conectar(extra = {}) {
  const servidor = new McpServer({ name: "prueba", version: "0" });
  registrarCalendario(servidor, {
    binario, carpetaConfig: join(tmp, "config"), registrar: (l) => registro.push(l), origen: "prueba", ...extra,
  });
  const [a, b] = InMemoryTransport.createLinkedPair();
  const cliente = new Client({ name: "prueba", version: "0" });
  await Promise.all([servidor.connect(a), cliente.connect(b)]);
  return cliente;
}

const cliente = await conectar();
const llamar = async (name, args = {}) => {
  const r = await cliente.callTool({ name, arguments: args });
  return { error: !!r.isError, texto: r.content[0].text };
};
const ultimasArgs = () => readFileSync(registroFalso, "utf8").trim().split("\n").filter(Boolean).map(JSON.parse).at(-1);

let ok = 0;
const prueba = async (nombre, fn) => {
  try { await fn(); ok++; console.log(`✓ ${nombre}`); }
  catch (e) { console.log(`✗ ${nombre}\n  ${e.message}`); process.exitCode = 1; }
};

const ID = "SIMPLE_bbbbbbbbbbb==";
const SERIE = "SERIE_aaaaaaaaaaaa==";

if (real) {
  // Cada llamada debe pasar el análisis de opciones de proton-cal y fallar solo por la sesión.
  const casos = [
    ["calendario_listar_calendarios", {}],
    ["calendario_ver_agenda", { desde: "2026-11-20 08:00", dias: 30, calendarios: ["Personal", "Trabajo, casa"] }],
    ["calendario_ver_agenda", { todos: true }],
    ["calendario_ver_evento", { id: ID, calendario: "Personal" }],
    ["calendario_crear_evento", {
      titulo: "--help -x", inicio: "2026-12-01 10:00", fin: "2026-12-01 11:00", descripcion: "-raro\nlínea 2",
      lugar: "--location=otra", calendario: "Personal", zona_horaria: "Europe/London",
      repetir: { frecuencia: "weekly", cada: 2, veces: 5 }, recordatorios: ["15m", "email:1h"],
    }],
    ["calendario_crear_evento", { titulo: "Puente", inicio: "2026-12-07", fin: "2026-12-08", todo_el_dia: true, sin_recordatorios: true }],
    ["calendario_editar_evento", {
      id: ID, ocurrencia: "2026-11-24 19:00", titulo: "Nuevo", inicio: "2026-11-24 20:00", descripcion: "",
      lugar: "", repetir: { frecuencia: "daily", hasta: "2026-12-31" }, recordatorios_por_defecto: true,
    }],
    ["calendario_editar_evento", { id: ID, dejar_de_repetir: true, sin_recordatorios: true }],
    ["calendario_borrar_evento", { id: ID, ocurrencia: "2026-11-24 19:00", calendario: "Personal" }],
    ["calendario_borrar_evento", { id: ID, toda_la_serie: true }],
  ];
  for (const [h, a] of casos) {
    await prueba(`${h} ${JSON.stringify(a).slice(0, 60)}`, async () => {
      const r = await llamar(h, a);
      assert.ok(r.error, "debería fallar sin sesión");
      assert.match(r.texto, /sesión de Proton Calendar/, r.texto);
    });
  }
} else {
  await prueba("lista las 6 herramientas", async () => {
    const { tools } = await cliente.listTools();
    assert.deepEqual(tools.map((t) => t.name).sort(), [
      "calendario_borrar_evento", "calendario_crear_evento", "calendario_editar_evento",
      "calendario_listar_calendarios", "calendario_ver_agenda", "calendario_ver_evento",
    ]);
    assert.equal(tools.find((t) => t.name === "calendario_ver_agenda").annotations.readOnlyHint, true);
    assert.equal(tools.find((t) => t.name === "calendario_borrar_evento").annotations.destructiveHint, true);
  });

  await prueba("listar calendarios", async () => {
    const r = await llamar("calendario_listar_calendarios");
    assert.deepEqual(JSON.parse(r.texto), [{ nombre: "Personal", id: "CAL1", por_defecto: true }, { nombre: "Trabajo", id: "CAL2" }]);
    assert.deepEqual(ultimasArgs(), ["calendars", "--output=json", "--no-color"]);
  });

  await prueba("agenda: ocurrencia en hora local y todo el día con último día incluido", async () => {
    const r = await llamar("calendario_ver_agenda", { desde: "2026-11-17", dias: 30 });
    const [gym, puente] = JSON.parse(r.texto);
    assert.equal(gym.ocurrencia, "2026-11-23 19:00");
    assert.equal(gym.se_repite, "FREQ=WEEKLY;BYDAY=MO");
    assert.deepEqual(
      { inicio: puente.inicio, ultimo_dia: puente.ultimo_dia, todo_el_dia: puente.todo_el_dia },
      { inicio: "2026-12-07", ultimo_dia: "2026-12-08", todo_el_dia: true },
    );
    assert.deepEqual(ultimasArgs().slice(0, 3), ["events", "--days=30", "--from=2026-11-17"]);
  });

  await prueba("agenda: todos y calendarios a la vez se rechaza", async () => {
    const r = await llamar("calendario_ver_agenda", { todos: true, calendarios: ["X"] });
    assert.ok(r.error);
  });

  await prueba("crear: título con guiones va detrás de «--»", async () => {
    const r = await llamar("calendario_crear_evento", {
      titulo: "--all-day", inicio: "2026-12-01 10:00", lugar: "-x", recordatorios: ["15m"],
    });
    assert.ok(!r.error, r.texto);
    const a = ultimasArgs();
    assert.deepEqual(a.slice(a.indexOf("--")), ["--", "--all-day"]);
    assert.ok(a.indexOf("--output=json") < a.indexOf("--"));
    assert.ok(a.includes("--location=-x") && a.includes("--reminder=15m") && !a.slice(0, a.indexOf("--")).includes("--all-day"));
    assert.match(registro.at(-1), /^calendario crear .*\(prueba\)$/);
  });

  await prueba("crear: valida fechas y recordatorios", async () => {
    for (const malo of [
      { titulo: "x", inicio: "mañana" },
      { titulo: "x", inicio: "2026-12-01 10:00", recordatorios: ["15 minutos"] },
      { titulo: "", inicio: "2026-12-01" },
      { titulo: "x", inicio: "2026-12-01", repetir: { frecuencia: "weekly", veces: 3, hasta: "2027-01-01" } },
    ]) {
      const r = await llamar("calendario_crear_evento", malo);
      assert.ok(r.error, `debería rechazar ${JSON.stringify(malo)}`);
    }
  });

  await prueba("editar: cadena vacía borra el lugar; sin cambios se rechaza", async () => {
    let r = await llamar("calendario_editar_evento", { id: ID, lugar: "" });
    assert.ok(!r.error, r.texto);
    assert.ok(ultimasArgs().includes("--location="));
    r = await llamar("calendario_editar_evento", { id: ID, calendario: "Personal" });
    assert.ok(r.error && /ningún cambio/.test(r.texto));
  });

  await prueba("editar una repetición", async () => {
    const r = await llamar("calendario_editar_evento", { id: SERIE, ocurrencia: "2026-11-17 19:00", inicio: "2026-11-17 20:00" });
    assert.equal(JSON.parse(r.texto).solo_esa_repeticion, true);
  });

  await prueba("borrar: una serie sin toda_la_serie se rechaza y no borra", async () => {
    const r = await llamar("calendario_borrar_evento", { id: SERIE });
    assert.ok(r.error && /se repite/.test(r.texto));
    assert.equal(ultimasArgs()[0], "get");
  });

  await prueba("borrar: evento simple, serie entera y una repetición", async () => {
    assert.ok(!(await llamar("calendario_borrar_evento", { id: ID })).error);
    assert.deepEqual(ultimasArgs(), ["delete", "event", "--output=json", "--no-color", "--", ID]);
    assert.ok(!(await llamar("calendario_borrar_evento", { id: SERIE, toda_la_serie: true })).error);
    assert.ok(!(await llamar("calendario_borrar_evento", { id: SERIE, ocurrencia: "2026-11-17 19:00" })).error);
    assert.ok(ultimasArgs().includes("--occurrence=2026-11-17 19:00"));
  });

  await prueba("IDs raros se rechazan antes de llamar a proton-cal", async () => {
    const antes = readFileSync(registroFalso, "utf8");
    for (const id of ["--help", "a b c d e f g h", "../../etc/passwd"]) {
      assert.ok((await llamar("calendario_ver_evento", { id })).error);
    }
    assert.equal(readFileSync(registroFalso, "utf8"), antes);
  });

  await prueba("sesión caducada: mensaje claro", async () => {
    writeFileSync(join(tmp, "fallo-sesion"), "");
    const r = await llamar("calendario_ver_agenda");
    rmSync(join(tmp, "fallo-sesion"));
    assert.ok(r.error && /iniciar-sesion\.sh/.test(r.texto));
  });

  await prueba("binario que no existe: mensaje claro", async () => {
    const c2 = await conectar({ binario: join(tmp, "no-existe") });
    const r = await c2.callTool({ name: "calendario_listar_calendarios", arguments: {} });
    assert.ok(r.isError && /instalar-proton-cal/.test(r.content[0].text));
  });

  await prueba("resumirEvento: evento de un solo día de todo el día", async () => {
    const r = resumirEvento({ id: "x", summary: "Día", start: "2026-12-25T00:00:00Z", end: "2026-12-26T00:00:00Z", all_day: true }, "Europe/Madrid");
    assert.equal(r.inicio, "2026-12-25");
    assert.equal(r.ultimo_dia, undefined);
  });
}

console.log(`\n${ok} pruebas bien${process.exitCode ? ", alguna ha fallado" : ""}.`);
