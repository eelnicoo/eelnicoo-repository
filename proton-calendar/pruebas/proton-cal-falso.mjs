#!/usr/bin/env node
// proton-cal de mentira para las pruebas: apunta los argumentos en $FALSO_REGISTRO
// (y falla sin sesión si existe $FALSO_MARCA), y responde con JSON del mismo formato que la v0.1.3.
import { appendFileSync, existsSync } from "node:fs";

const args = process.argv.slice(2);
appendFileSync(process.env.FALSO_REGISTRO, JSON.stringify(args) + "\n");
const tiene = (x) => args.includes(x);
const salida = (o) => process.stdout.write(JSON.stringify(o));

const SERIE = "SERIE_aaaaaaaaaaaa==";
const SIMPLE = "SIMPLE_bbbbbbbbbbb==";

if (existsSync(process.env.FALSO_MARCA)) {
  process.stderr.write("Error: not logged in; run `proton-cal login` first\n");
  process.exit(1);
}

if (tiene("calendars")) {
  salida([
    { id: "CAL1", name: "Personal", color: "#8080FF", type: 0, is_default: true },
    { id: "CAL2", name: "Trabajo", color: "#FF8080", type: 0, is_default: false },
  ]);
} else if (tiene("events")) {
  salida([
    {
      id: SERIE, summary: "Gimnasio", start: "2026-11-23T19:00:00+01:00", end: "2026-11-23T20:00:00+01:00",
      all_day: false, recurring: true, edited_occurrence: false, occurrence_start_ts: 1795456800,
      rrule: "FREQ=WEEKLY;BYDAY=MO", calendar_name: "Personal",
    },
    {
      id: SIMPLE, summary: "Puente", start: "2026-12-07T00:00:00Z", end: "2026-12-09T00:00:00Z",
      all_day: true, recurring: false, edited_occurrence: false, calendar_name: "Personal",
      notifications: [{ type: "notify", trigger: "-PT15H" }],
    },
  ]);
} else if (tiene("get")) {
  const id = args.at(-1);
  salida({
    id, summary: id === SERIE ? "Gimnasio" : "Cena", start: "2026-11-20T21:00:00+01:00",
    end: "2026-11-20T23:00:00+01:00", all_day: false, recurring: id === SERIE, edited_occurrence: false,
    location: "Casa", attendees: [{ email: "ana@example.com", cn: "Ana", status: "accepted" }],
  });
} else if (tiene("create")) {
  salida({ id: "NUEVO_cccccccccc==", summary: args.at(-1), start_ts: 0, end_ts: 0, all_day: tiene("--all-day") });
} else if (tiene("update")) {
  salida({ updated: true, edited_occurrence: args.some((a) => a.startsWith("--occurrence=")), removed_exceptions: 0 });
} else if (tiene("delete")) {
  salida({ deleted: true });
} else {
  process.stderr.write("Error: orden desconocida\n");
  process.exit(2);
}
