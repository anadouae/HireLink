#!/usr/bin/env python3
"""
Générateur de codes TOTP (RFC 6238) pour HireLink — Python standard, rien à installer.

Remplace l'application d'authentification du téléphone (Google Authenticator, Microsoft Authenticator, Aegis…)
pour tester la double authentification de l'administrateur.

Utilisation (Windows : ouvrir PowerShell dans le dossier du projet) :
    python docs\\outils\\totp.py JBSWY3DPEHPK3PXP          # affiche le code actuel
    python docs\\outils\\totp.py JBSWY3DPEHPK3PXP --watch  # affiche un nouveau code toutes les 30 s
    python docs\\outils\\totp.py "otpauth://totp/HireLink:admin@hirelink.ma?secret=JBSWY3DPEHPK3PXP&issuer=HireLink"

La clé (« secret ») est celle affichée sous le QR code lors de la première connexion de l'administrateur.
Elle est personnelle : ne la partagez pas et ne la publiez pas.
"""
import base64
import hashlib
import hmac
import struct
import sys
import time
from urllib.parse import parse_qs, urlparse


def extract_secret(value: str) -> str:
    value = value.strip()
    if value.lower().startswith("otpauth://"):
        qs = parse_qs(urlparse(value).query)
        if "secret" not in qs:
            sys.exit("URI otpauth sans paramètre secret.")
        value = qs["secret"][0]
    return "".join(value.split()).upper()


def totp(secret_b32: str, at: float | None = None, digits: int = 6, step: int = 30) -> str:
    padded = secret_b32 + "=" * (-len(secret_b32) % 8)
    try:
        key = base64.b32decode(padded, casefold=True)
    except Exception:
        sys.exit("Clé invalide : elle ne contient que des lettres A-Z et des chiffres 2-7.")
    counter = int((time.time() if at is None else at) // step)
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    code = (struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % (10 ** digits)
    return str(code).zfill(digits)


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        print(__doc__)
        sys.exit(1)
    secret = extract_secret(args[0])
    if "--watch" not in sys.argv:
        remaining = 30 - int(time.time()) % 30
        print(f"{totp(secret)}   (valable encore {remaining} s)")
        return
    last = None
    try:
        while True:
            code = totp(secret)
            if code != last:
                print(f"{time.strftime('%H:%M:%S')}  code : {code}", flush=True)
                last = code
            time.sleep(1)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
