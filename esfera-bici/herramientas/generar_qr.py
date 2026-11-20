#!/usr/bin/env python3
"""Crea el QR de instalación para el modo desarrollador de la app Zepp.

Uso:  python3 herramientas/generar_qr.py https://servidor/ruta/ruta-bici.zpk
El QR contiene la misma dirección con el esquema cambiado a zpkd1://
"""
import os
import sys

import segno

url = sys.argv[1]
contenido = "zpkd1://" + url.split("://", 1)[-1]
salida = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "instalar", "qr.png")
segno.make(contenido, error="m").save(salida, scale=10, border=3)
print(contenido)
print(salida)
