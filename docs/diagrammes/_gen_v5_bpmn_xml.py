# -*- coding: utf-8 -*-
"""Exporte le processus de recrutement HireLink en BPMN 2.0 XML (import Camunda Modeler / Bizagi).

Le rendu visuel de reference reste v5/09_bpmn_processus_recrutement.png (PlantUML) ;
ce fichier sert quand le professeur veut ouvrir le processus dans un outil BPMN reel.
Lance :  python docs/diagrammes/_gen_v5_bpmn_xml.py
"""
import pathlib

OUT = pathlib.Path(__file__).parent / "v5" / "processus_recrutement_hirelink.bpmn"

# (id, type, libelle, couloir, x, y) ; type in {task, user, gateway, start, end}
NODES = [
    ("start", "startEvent", "Candidat consulte les offres", "candidat", 120, 40),
    ("profil", "task", "Prépare son profil (CV, compétences)", "candidat", 260, 40),
    ("postule", "task", "Postule (lettre + CV)", "candidat", 420, 40),
    ("controle", "task", "Contrôle anti-doublon", "systeme", 120, 220),
    ("score", "task", "Calcule le score et notifie le recruteur", "systeme", 280, 220),
    ("screening", "task", "Trie le dossier (SCREENING)", "recruteur", 460, 400),
    ("g1", "exclusiveGateway", "Présélection ?", "recruteur", 620, 400),
    ("shortlist", "task", "Met en shortlist (SHORTLISTED)", "recruteur", 720, 400),
    ("g2", "exclusiveGateway", "Entretien ?", "recruteur", 880, 400),
    ("planifie", "task", "Planifie l'entretien (sur site ou visio)", "recruteur", 980, 400),
    ("repond", "task", "Reçoit l'invitation et répond", "candidat", 560, 130),
    ("g3", "exclusiveGateway", "Réponse avant échéance ?", "candidat", 720, 130),
    ("rappel", "task", "Rappel J-1 (une seule fois)", "systeme", 880, 220),
    ("entretien", "task", "Mène l'entretien et saisit la scorecard", "recruteur", 1080, 400),
    ("decision", "task", "Décide embauche ou refus", "recruteur", 1240, 400),
    ("ics", "task", "Ajoute l'entretien au calendrier (.ics)", "candidat", 900, 40),
    ("endOk", "endEvent", "Entretien réalisé", "candidat", 1060, 40),
    ("refus1", "task", "Refuse avec un motif (REJECTED)", "recruteur", 720, 520),
    ("notifRefus", "task", "Notifie le candidat du refus", "systeme", 440, 300),
    ("endRefus", "endEvent", "Candidature refusée", "candidat", 300, 130),
    ("sla", "task", "Constate le dépassement de SLA", "recruteur", 1080, 520),
    ("endSla", "endEvent", "Sans réponse", "recruteur", 1240, 520),
]

LANES = [("candidat", "Candidat", 0, 180), ("systeme", "HireLink (système)", 180, 180),
         ("recruteur", "Recruteur", 360, 220)]

# (id, source, cible, libelle)
FLOWS = [
    ("f1", "start", "profil", ""),
    ("f2", "profil", "postule", ""),
    ("f3", "postule", "controle", ""),
    ("f4", "controle", "score", ""),
    ("f5", "score", "screening", ""),
    ("f6", "screening", "g1", ""),
    ("f7", "g1", "shortlist", "oui"),
    ("f8", "g1", "refus1", "refus direct"),
    ("f9", "shortlist", "g2", ""),
    ("f10", "g2", "planifie", "oui"),
    ("f11", "g2", "refus1", "refus motivé"),
    ("f12", "planifie", "repond", ""),
    ("f13", "repond", "g3", ""),
    ("f14", "g3", "rappel", "acceptée"),
    ("f15", "g3", "sla", "aucune réponse"),
    ("f16", "rappel", "entretien", ""),
    ("f17", "entretien", "decision", ""),
    ("f18", "decision", "ics", ""),
    ("f19", "ics", "endOk", ""),
    ("f20", "refus1", "notifRefus", ""),
    ("f21", "notifRefus", "endRefus", ""),
    ("f22", "sla", "endSla", ""),
]

BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
DI_NS = "http://www.omg.org/spec/BPMN/20100524/DI"
DC_NS = "http://www.omg.org/spec/DD/20100524/DC"


def xml() -> str:
    pos = {n[0]: (n[4], n[5]) for n in NODES}
    h = {"start": 36, "end": 36, "gateway": 50, "task": 80, "user": 80}
    w = {"start": 36, "end": 36, "gateway": 50, "task": 120, "user": 120}
    elements = []
    for nid, ntype, label, lane, x, y in NODES:
        if ntype == "task":
            elements.append(f'      <bpmn:task id="{nid}" name="{label}"/>')
        elif ntype == "exclusiveGateway":
            elements.append(f'      <bpmn:exclusiveGateway id="{nid}" name="{label}"/>')
        elif ntype == "startEvent":
            elements.append(f'      <bpmn:startEvent id="{nid}" name="{label}"/>')
        else:
            elements.append(f'      <bpmn:endEvent id="{nid}" name="{label}"/>')
    flows = [f'      <bpmn:sequenceFlow id="{fid}" sourceRef="{s}" targetRef="{t}"'
             + (f' name="{lbl}"' if lbl else "") + "/>" for fid, s, t, lbl in FLOWS]
    lanes = []
    for lid, lname, ly, lh in LANES:
        ids = [n[0] for n in NODES if n[3] == lid]
        lanes.append(f'      <bpmn:lane id="lane_{lid}" name="{lname}">\n'
                     + "".join(f'        <bpmn:flowNodeRef>{i}</bpmn:flowNodeRef>\n' for i in ids)
                     + "      </bpmn:lane>")
    shapes = []
    for nid, ntype, label, lane, x, y in NODES:
        k = "task" if ntype == "task" else ("gateway" if "Gateway" in ntype else ("start" if "start" in ntype else "end"))
        shapes.append(
            f'        <bpmndi:BPMNShape id="{nid}_di" bpmnElement="{nid}">\n'
            f'          <dc:Bounds x="{x}" y="{y + h[k] // 2}" width="{w[k]}" height="{h[k]}"/>\n'
            f'        </bpmndi:BPMNShape>')
    lane_shapes = []
    for lid, lname, ly, lh in LANES:
        lane_shapes.append(f'        <bpmndi:BPMNShape id="lane_{lid}_di" bpmnElement="lane_{lid}" isHorizontal="true">\n'
                           f'          <dc:Bounds x="60" y="{ly + 40}" width="1360" height="{lh}"/>\n'
                           f'        </bpmndi:BPMNShape>')
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="{BPMN_NS}" xmlns:bpmndi="{DI_NS}"
                  xmlns:dc="{DC_NS}"
                  id="Definitions_hirelink_v5" targetNamespace="http://hirelink.ma/bpmn"
                  exporter="genere par docs/diagrammes/_gen_v5_bpmn_xml.py" exporterVersion="1.0">
  <bpmn:process id="processus_recrutement_hirelink" name="Processus de recrutement HireLink" isExecutable="false">
{chr(10).join(elements + flows)}
    <bpmn:laneSet>
{chr(10).join(lanes)}
    </bpmn:laneSet>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="processus_recrutement_hirelink">
{chr(10).join(lane_shapes + shapes)}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
"""


def main() -> int:
    import xml.etree.ElementTree as ET
    text = xml()
    ET.fromstring(text)  # refuse d'ecrire un XML mal forme
    OUT.write_text(text, encoding="utf-8")
    print(f"OK  {OUT}  {OUT.stat().st_size} o  (XML bien forme)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
