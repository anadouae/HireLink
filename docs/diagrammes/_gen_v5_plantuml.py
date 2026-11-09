# -*- coding: utf-8 -*-
"""Genere les diagrammes UML + BPMN de HireLink v5.

Sources .puml ecrites dans docs/diagrammes/v5/, PNG rendus par le serveur PlantUML
(https://www.plantuml.com/plantuml). Aucun binaire local requis.

Le drapeau --local remplace le serveur par « java -jar plantuml.jar » (variable
d'environnement PLANTUML_JAR, ou --jar chemin) : le serveur public se sature et
rend parfois une page HTML de consentement a la place du PNG.
Lance :  python docs/diagrammes/_gen_v5_plantuml.py [--svg] [--only nom] [--local]
"""
import os
import re
import shutil
import subprocess
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


def label_count(src: str, ext: str) -> int:
    """Nombre d'étiquettes réellement dessinées, mesuré sur le SVG.

    Le serveur public renvoie parfois un PNG « dégradé » où toutes les étiquettes ont
    disparu, sans code d'erreur. Le nombre de textes du SVG sert de référence pour
    rejeter ces rendus.
    """
    body = fetch(src, ext)
    if not body.startswith(b"<svg"):
        return -1
    return body.decode("utf-8", "ignore").count("<text")


def avec_skin(name: str, src: str) -> str:
    if "@startuml " + name in src:
        return src.replace("@startuml " + name, "@startuml " + name + SKIN, 1)
    return src


def render_local(name: str, src: str, jar: str) -> pathlib.Path:
    """Rendu par le moteur Java : même langage, mais aucun serveur à supplier."""
    OUT.mkdir(parents=True, exist_ok=True)
    src = avec_skin(name, src)
    puml = OUT / f"{name}.puml"
    puml.write_text(src, encoding="utf-8")
    cible = OUT / f"{name}.png"
    travail = pathlib.Path(os.environ.get("TEMP", ".")) / f"plantuml_{name}"
    travail.mkdir(exist_ok=True)
    subprocess.run(["java", "-jar", jar, "-tpng", "-o", str(travail), str(puml)],
                   check=True, capture_output=True)
    # Le jar nomme l'image d'après l'identifiant du @startuml, pas d'après le fichier source.
    produits = sorted(travail.glob("*.png"))
    if not produits:
        raise RuntimeError(f"{name}: le jar n'a produit aucune image")
    cible.write_bytes(produits[0].read_bytes())
    shutil.rmtree(travail, ignore_errors=True)
    return cible


def render(name: str, src: str, as_svg: bool) -> pathlib.Path:
    OUT.mkdir(parents=True, exist_ok=True)
    src = avec_skin(name, src)
    (OUT / f"{name}.puml").write_text(src, encoding="utf-8")
    ext = "svg" if as_svg else "png"
    expected = label_count(src, "svg")
    signature = b"\x89PNG\r\n\x1a\n" if ext == "png" else b"<svg"
    # Le serveur public peut répondre un PNG sans aucune étiquette, en silence : on rend
    # trois fois et on garde la plus grande image, celle qui porte les textes.
    best = b""
    for _ in range(3):
        body = fetch(src, ext)
        if body.startswith(signature) and len(body) > len(best):
            best = body
    if not best:
        raise RuntimeError(f"{name}: sortie inattendue")
    if expected >= 0 and len(best) < 20000:
        print(f"ATTENTION {name}: {len(best)} o pour {expected} étiquettes, rendu à revérifier")
    target = OUT / f"{name}.{ext}"
    target.write_bytes(best)
    return target


def main() -> int:
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]
    as_svg = "--svg" in sys.argv
    jar = None
    if "--local" in sys.argv:
        jar = (os.environ.get("PLANTUML_JAR")
               or (sys.argv[sys.argv.index("--jar") + 1] if "--jar" in sys.argv else None))
        if not jar or not pathlib.Path(jar).exists():
            print("PLANTUML_JAR (ou --jar) doit pointer sur plantuml.jar ; rendu serveur à la place.")
            jar = None
    import diagrams_v5
    diagrams = diagrams_v5.DIAGRAMS
    for name, src in diagrams.items():
        if only and only not in name:
            continue
        try:
            path = render_local(name, src, jar) if jar else render(name, src, as_svg)
            print(f"OK  {path.name:44s} {path.stat().st_size:>8} o")
        except Exception as exc:  # noqa: BLE001 - un diagramme casse ne doit pas bloquer les autres
            print(f"ECHEC {name}: {exc}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
