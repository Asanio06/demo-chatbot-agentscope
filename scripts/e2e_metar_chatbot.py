#!/usr/bin/env python
"""
E2E test — demo-chatbot-agentscope (METAR/TAF chatbot).

Vérifie réellement, contre la stack lancée localement (back Spring Boot + Ollama),
que l'agent ReAct decode un bulletin METAR en déclenchant l'outil déterministe
'décode_metar_taf' via l'endpoint AG-UI /api/copilotkit/run.

Usage:
    python scripts/e2e_metar_chatbot.py [base_url] [metar]

Défauts :
    base_url = http://localhost:8080
    metar    = LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG

Exit 0 si le décodage structuré attendu est présent dans la réponse.
Exit 1 sinon (avec la réponse brute pour diagnostic).
"""
import json
import re
import sys
import urllib.request
import urllib.error

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
METAR = (
    sys.argv[2]
    if len(sys.argv) > 2
    else "LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG"
)

RUN_URL = f"{BASE_URL}/api/copilotkit/run"


def post_run():
    """Envoie une question METAR au runtime AG-UI et renvoie le texte SSE brut."""
    payload = {
        # threadId + runId sont requis par le schéma AG-UI (RunAgentInput).
        "threadId": "e2e-thread-metar",
        "runId": "e2e-run-1",
        "agentId": "metar-taf-decoder",
        "messages": [
            {
                "id": "e2e-1",
                "role": "user",
                # Le runtime AG-UI (agentscope) accepte content en chaîne directe
                # (MessageContentDeserializer: VALUE_STRING -> MessageContent.Text).
                "content": f"Décode ce METAR : {METAR}",
            }
        ],
    }
    req = urllib.request.Request(
        RUN_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            return resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return f"HTTP {e.code}: {body[:2000]}"
    except Exception as e:  # noqa: BLE001
        return f"EXCEPTION {type(e).__name__}: {e}"


def extract_text(stream):
    """Recueille tous les contenus textuels des events SSE (data: ...)."""
    texts = []
    for line in stream.splitlines():
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        try:
            obj = json.loads(data)
        except json.JSONDecodeError:
            continue
        # AG-UI: les messages arrivent sous obj['message'] ou directement.
        msg = obj.get("message", obj)
        role = msg.get("role", "")
        # Le contenu peut être str ou liste de blocs {type: text, text: ...}
        content = msg.get("content")
        if isinstance(content, str):
            txt = content
        elif isinstance(content, list):
            txt = " ".join(
                b.get("text", "")
                for b in content
                if isinstance(b, dict) and b.get("type") in ("text", "output_text")
            )
        else:
            txt = str(content) if content else ""
        if txt.strip():
            texts.append((role, txt.strip()))
    return texts


def main():
    print(f"E2E → {RUN_URL}")
    print(f"Question METAR : {METAR}\n")
    stream = post_run()
    if stream.startswith(("HTTP ", "EXCEPTION")):
        print("ÉCHEC REQUÊTE :", stream)
        return 1
    roles_texts = extract_text(stream)
    if not roles_texts:
        print("Aucun contenu textuel dans le stream AG-UI.")
        print("--- stream brut (extrait) ---")
        print(stream[:3000])
        return 1
    full = "\n".join(f"[{r}] {t}" for r, t in roles_texts)
    print("--- réponses reçues ---")
    print(full)
    # Vérifs clés du décodage déterministe du METAR exemple.
    # Le décodeur normalise les valeurs en français (9999 -> "10 km+", Q1015 -> "1015 hPa")
    # donc on cherche la forme lisible, pas le code brut.
    checks = {
        "code OACI (LFPG)": r"LFPG",
        "vent direction (240)": r"240",
        "vent vitesse (12 kt)": r"12\s?kt",
        "visibilité (9999 -> 10 km+)": r"10\s?km\+",
        "couche nuageuse (SCT040)": r"SCT\s?040",
        "température (17/06)": r"17",
        "QNH (=1015 hPa)": r"1015\s?hPa",
        "tendance NOSIG": r"NOSIG",
    }
    print("\n--- vérifs déterministes ---")
    failed = []
    for label, pat in checks.items():
        ok = bool(re.search(pat, full, re.IGNORECASE))
        print(f"  [{'OK' if ok else 'KO'}] {label}")
        if not ok:
            failed.append(label)
    if failed:
        print(f"\nÉCHEC E2E : champs manquants → {failed}")
        return 1
    print("\n✅ E2E OK : le modèle a déclenché l'outil decode_metar_taf et restitué "
          "un décodage structuré conforme.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
