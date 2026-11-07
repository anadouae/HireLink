# -*- coding: utf-8 -*-
"""Genere les diagrammes UML + BPMN de HireLink v5.

Sources .puml ecrites dans docs/diagrammes/v5/, PNG rendus par le serveur PlantUML
(https://www.plantuml.com/plantuml). Aucun binaire local requis.
Lance :  python docs/diagrammes/_gen_v5_plantuml.py [--svg] [--only nom]
"""
import re
import sys
import time
import zlib
import pathlib
import urllib.request

HERE = pathlib.Path(__file__).parent
OUT = HERE / "v5"
SERVER = "https://www.plantuml.com/plantuml"
UA = "hirelink-docs/1.0"
ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"


def encode64(data: bytes) -> str:
    res = ""
    for i in range(0, len(data), 3):
        chunk = data[i:i + 3]
        n = (chunk[0] << 16) + ((chunk[1] << 8) if len(chunk) > 1 else 0) + (chunk[2] if len(chunk) > 2 else 0)
        res += ALPHABET[(n >> 18) & 63] + ALPHABET[(n >> 12) & 63] + ALPHABET[(n >> 6) & 63] + ALPHABET[n & 63]
    return res


def plantuml_encode(src: str) -> str:
    raw = src.encode("utf-8")
    comp = zlib.compressobj(9, zlib.DEFLATED, -zlib.MAX_WBITS)
    data = comp.compress(raw) + comp.flush()
    return encode64(data)


SKIN = """
skinparam backgroundColor #FFFFFF
skinparam defaultFontName SansSerif
skinparam shadowing false
skinparam roundCorner 8
skinparam ArrowColor #475569
skinparam BorderColor #1a1214
"""


def _post(src: str, ext: str, field: str, host: str) -> bytes:
    import urllib.parse
    data = urllib.parse.urlencode({field: src}).encode()
    req = urllib.request.Request(f"{host}/{ext}", data=data, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=90) as resp:
        return resp.read()


def fetch(src: str, ext: str) -> bytes:
    """GET d'abord, puis POST, avec quelques tentatives : le serveur public renvoie parfois une
    page HTML de consentement (anti-robot) quand il est sollicite en rafale."""
    signature = b"\x89PNG\r\n\x1a\n" if ext == "png" else b"<svg"

    def ok(body: bytes) -> bool:
        return body.startswith(signature)

    hosts = (SERVER, "https://www.plantuml.com/plantuml")
    last = b""
    for attempt in range(4):
        host = hosts[attempt % len(hosts)]
        try:
            url = f"{host}/{ext}/{plantuml_encode(src)}"
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=90) as resp:
                last = resp.read()
                if ok(last):
                    return last
        except Exception:  # noqa: BLE001 - on retente en POST
            pass
        for field in ("source", "src"):
            try:
                last = _post(src, ext, field, host)
            except Exception:  # noqa: BLE001
                continue
            if ok(last):
                return last
        time.sleep(3 * (attempt + 1))
    return last


def render(name: str, src: str, as_svg: bool) -> pathlib.Path:
    OUT.mkdir(parents=True, exist_ok=True)
    src = src.replace("@startuml " + name, "@startuml " + name + SKIN, 1) if "@startuml" in src else src
    (OUT / f"{name}.puml").write_text(src, encoding="utf-8")
    ext = "svg" if as_svg else "png"
    body = fetch(src, ext)
    target = OUT / f"{name}.{ext}"
    signature = b"\x89PNG\r\n\x1a\n" if ext == "png" else b"<svg"
    if not body.startswith(signature):
        raise RuntimeError(f"{name}: sortie inattendue {body[:200]!r}")
    target.write_bytes(body)
    return target


def main() -> int:
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]
    as_svg = "--svg" in sys.argv
    import diagrams_v5
    diagrams = diagrams_v5.DIAGRAMS
    for name, src in diagrams.items():
        if only and only not in name:
            continue
        try:
            path = render(name, src, as_svg)
            print(f"OK  {path.name:44s} {path.stat().st_size:>8} o")
        except Exception as exc:  # noqa: BLE001 - un diagramme casse ne doit pas bloquer les autres
            print(f"ECHEC {name}: {exc}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
