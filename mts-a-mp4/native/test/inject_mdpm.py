#!/usr/bin/env python3
"""Mete un SEI con fecha AVCHD (MDPM) delante de cada fotograma IDR de un
H.264 en formato Annex B, imitando lo que graban las videocámaras.

uso: inject_mdpm.py entrada.h264 salida.h264 AAAA MM DD hh mm ss TZBYTE
"""
import sys

UUID = bytes.fromhex("17ee8c60f84d11d98cd60800200c9a66")


def bcd(v):
    return ((v // 10) << 4) | (v % 10)


def escape(rbsp):
    out = bytearray()
    zeros = 0
    for b in rbsp:
        if zeros >= 2 and b <= 3:
            out.append(3)
            zeros = 0
        out.append(b)
        zeros = zeros + 1 if b == 0 else 0
    return bytes(out)


def sei_nal(y, mo, d, h, mi, s, tz):
    entries = [
        (0x13, bytes([0x00, 0x00, 0x00, 0x00])),  # timecode con ceros (fuerza bytes 03)
        (0x18, bytes([tz, bcd(y // 100), bcd(y % 100), bcd(mo)])),
        (0x19, bytes([bcd(d), bcd(h), bcd(mi), bcd(s)])),
        (0x70, bytes([0x00, 0x00, 0x00, 0x01])),
    ]
    mdpm = b"MDPM" + bytes([len(entries)]) + b"".join(bytes([t]) + v for t, v in entries)
    payload = UUID + mdpm
    size = len(payload)
    msg = bytearray([5])
    while size >= 255:
        msg.append(255)
        size -= 255
    msg.append(size)
    msg += payload
    msg.append(0x80)  # rbsp_trailing_bits
    return b"\x00\x00\x00\x01\x06" + escape(bytes(msg))


def split_nals(data):
    i, n = 0, len(data)
    starts = []
    while i < n - 3:
        if data[i] == 0 and data[i + 1] == 0 and data[i + 2] == 1:
            sc = i - 1 if i > 0 and data[i - 1] == 0 else i
            starts.append((sc, i + 3))
            i += 3
        else:
            i += 1
    nals = []
    for k, (sc, payload) in enumerate(starts):
        end = starts[k + 1][0] if k + 1 < len(starts) else n
        nals.append((data[sc:end], data[payload] & 0x1F))
    return nals


def main():
    src, dst = sys.argv[1], sys.argv[2]
    y, mo, d, h, mi, s = map(int, sys.argv[3:9])
    tz = int(sys.argv[9], 0)
    sei = sei_nal(y, mo, d, h, mi, s, tz)
    data = open(src, "rb").read()
    out = bytearray()
    count = 0
    for nal, typ in split_nals(data):
        if typ == 5:  # slice IDR
            out += sei
            count += 1
        out += nal
    open(dst, "wb").write(out)
    print(f"SEI MDPM insertados: {count}")


if __name__ == "__main__":
    main()
