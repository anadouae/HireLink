# -*- coding: utf-8 -*-
"""Génère le diagramme de classes FINAL HireLink (PNG UML)."""
from pathlib import Path
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle, FancyArrowPatch

OUT_DIR = Path(r"C:\Users\daurinia\Desktop\HireLink\docs\diagrammes")
out = OUT_DIR / "diagramme_classes_hirelink_FINAL.png"
out2 = OUT_DIR / "diagramme_classes_hirelink.png"

fig, ax = plt.subplots(figsize=(24, 15), dpi=160)
ax.set_xlim(0, 24)
ax.set_ylim(0, 15)
ax.axis("off")
fig.patch.set_facecolor("#f7f5f2")

ax.text(12, 14.55, "HireLink — Diagramme de classes FINAL", ha="center",
        fontsize=20, fontweight="bold", color="#1a1214")
ax.text(12, 14.15, "Modèle JPA réel · Spring Boot · Anti-fraude LinkedIn-like · Pipeline ATS",
        ha="center", fontsize=10.5, color="#5e5558")


def uml(x, y, w, h, title, attrs, methods=None, header="#7A2032"):
    methods = methods or []
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0", linewidth=1.15,
                                edgecolor="#1a1214", facecolor="white"))
    ax.add_patch(Rectangle((x, y + h - 0.40), w, 0.40, facecolor=header,
                           edgecolor="#1a1214", linewidth=1.15))
    ax.text(x + w / 2, y + h - 0.20, title, ha="center", va="center",
            fontsize=8.5, fontweight="bold", color="white")
    body_top = y + h - 0.40
    attr_h = min(0.24 * (len(attrs) + 0.2), max(h * 0.58, 0.8))
    ax.plot([x, x + w], [body_top - attr_h, body_top - attr_h], color="#c4b8ba", lw=0.8)
    yy = body_top - 0.18
    for a in attrs:
        ax.text(x + 0.08, yy, a, ha="left", va="center", fontsize=6.2,
                color="#1a1214", family="DejaVu Sans Mono")
        yy -= 0.22
    yy = body_top - attr_h - 0.16
    for m in methods:
        ax.text(x + 0.08, yy, m, ha="left", va="center", fontsize=6.2,
                color="#7A2032", family="DejaVu Sans Mono")
        yy -= 0.22


def link(x1, y1, x2, y2, label=""):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="-|>", color="#475569", lw=1.15, mutation_scale=9))
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.1, label, ha="center", fontsize=6,
                color="#475569",
                bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.92))


C_ID, C_REC, C_APP, C_EV, C_TR, C_FR = "#7A2032", "#1d4ed8", "#b45309", "#0f766e", "#15803d", "#9a3412"

# Identité
uml(0.25, 10.0, 3.7, 3.7, "User",
    ["+ id: Long", "+ email / passwordHash", "+ fullName / role: Role",
     "+ skills / soughtRoles…", "+ company / companyIce",
     "+ companyVerified: boolean", "+ verificationStatus",
     "+ planTier / createdAt"],
    ["+ login()", "+ updateProfile()"], C_ID)
uml(4.2, 10.8, 2.9, 2.6, "Membership",
    ["+ id: Long", "+ orgRole: OrgRole", "+ active", "+ invitedAt / acceptedAt"],
    ["+ hasPermission()"], C_ID)
uml(7.4, 10.2, 3.2, 3.2, "Organization",
    ["+ id / name / ice", "+ verified / verificationStatus",
     "+ verificationMethod", "+ logoUrl / website",
     "+ sector / city / employees", "+ presentation / planTier"],
    ["+ verify()", "+ enrichFromDirectory()"], C_ID)
uml(10.7, 11.6, 2.2, 1.6, "«enum» Role", ["CANDIDAT", "RECRUTEUR", "ADMIN"], [], "#334155")
uml(13.1, 11.6, 2.5, 1.6, "«enum» OrgRole", ["OWNER", "RECRUTEUR", "HM"], [], "#334155")
uml(15.8, 11.6, 2.6, 1.6, "«enum» PlanTier", ["ACCES", "ESSENTIEL", "PERF", "PREMIUM"], [], "#334155")
uml(18.6, 11.6, 4.9, 1.6, "«enum» ApplicationStatus",
    ["SUBMITTED→…→OFFER", "ACCEPTED / REJECTED", "WITHDRAWN"], [], "#334155")

# Recrutement
uml(0.25, 5.5, 3.9, 4.0, "JobOffer",
    ["+ id / title / description", "+ requiredSkills", "+ location / contractType",
     "+ responseSlaDays", "+ published", "+ suspended / reportCount",
     "+ quarantineReason", "+ createdAt"],
    ["+ publish()", "+ suspend()"], C_REC)
uml(4.4, 5.1, 4.5, 4.4, "Application",
    ["+ status: ApplicationStatus", "+ matchScore", "+ refusalReasonCode",
     "+ cv* / coverLetter", "+ interview* / advanceNote",
     "+ assignedHmUserId", "+ responseDeadline",
     "+ firstDecisionAt / slaBreachNotified", "+ submittedAt / updatedAt"],
    ["+ transitionTo()", "+ withdraw()"], C_APP)
uml(9.2, 6.8, 2.5, 2.5, "Tag", ["+ id", "+ name", "N–N job_offer_tags"], [], "#7c3aed")
uml(12.0, 6.4, 3.5, 2.9, "ApplicationStatusHistory",
    ["+ applicationId", "+ from / to", "+ actorUserId", "+ payloadJson", "+ createdAt"],
    ["«append-only R12»"], C_REC)
uml(15.8, 6.8, 3.0, 2.5, "RefusalReason",
    ["+ code / label", "+ active"], [], C_EV)
uml(19.2, 6.5, 4.3, 2.8, "OfferReport",
    ["+ jobOfferId", "+ reporterUserId", "+ reason", "+ createdAt"],
    ["3 signalements → suspend"], C_FR)

# Éval + transversal
uml(0.25, 1.9, 3.2, 3.0, "ScorecardCriterion",
    ["+ jobOfferId", "+ label / weight", "+ veto"], [], C_EV)
uml(3.7, 1.9, 3.3, 3.0, "ScorecardEntry",
    ["+ applicationId", "+ criterionId", "+ evaluatorUserId", "+ score / comment"],
    ["+ submit()"], C_EV)
uml(7.2, 1.9, 3.4, 3.0, "MatchingAnalysis",
    ["+ applicationId", "+ score", "+ covered / missing skills"],
    ["+ compute() F14"], C_TR)
uml(10.9, 1.9, 3.2, 3.0, "InAppNotification",
    ["+ userId", "+ title / body / kind", "+ readFlag"], [], C_TR)
uml(14.4, 1.9, 3.2, 3.0, "ConsentRecord",
    ["+ userId", "+ purpose", "+ granted"], [], C_TR)
uml(17.9, 1.9, 3.4, 3.0, "AiUsageLog",
    ["+ userId / orgId", "+ feature / provider", "+ creditsSpent", "+ success"],
    ["R16 débit si succès"], C_TR)
uml(21.5, 1.9, 2.2, 3.0, "EmailOtp",
    ["+ email / code", "+ purpose", "+ expires / consumed"], [], C_TR)

# Links
link(3.95, 11.5, 4.2, 11.5, "1 → *")
link(7.1, 11.5, 7.4, 11.5, "* → 1")
link(2.0, 10.0, 2.0, 9.5, "publie")
link(2.0, 10.0, 6.0, 9.5, "dépose")
link(4.15, 7.6, 4.4, 7.6, "reçoit")
link(8.9, 7.6, 9.2, 7.6, "*—*")
link(8.9, 7.2, 12.0, 7.4, "historique")
link(8.9, 6.8, 15.8, 7.4, "refus")
link(4.15, 7.0, 19.2, 7.4, "signalée")
link(2.0, 10.0, 20.5, 9.3, "signale")
link(2.0, 5.5, 1.8, 4.9, "critères")
link(6.5, 5.1, 8.5, 4.9, "matching")
link(6.5, 5.1, 5.0, 4.9, "notes")

# Legend
ax.add_patch(FancyBboxPatch((0.25, 0.15), 16.5, 1.5, boxstyle="round,pad=0.05",
                            linewidth=1, edgecolor="#c4b8ba", facecolor="white"))
ax.text(0.45, 1.35, "Notes d’alignement code (HireLink)", fontsize=8.5, fontweight="bold", color="#1a1214")
ax.text(0.45, 1.05, "• Anti-fraude : verificationStatus sur User/Org · publication si companyVerified · OfferReport (≥3 → suspended)", fontsize=7.2, color="#334155")
ax.text(0.45, 0.75, "• Profil candidat & entretien = attributs (pas d’entités CandidateProfile / Interview séparées)", fontsize=7.2, color="#334155")
ax.text(0.45, 0.45, "• Crédits IA = planTier + AiUsageLog · Matching = MatchingAnalysis (Jaccard local F14)", fontsize=7.2, color="#334155")
ax.text(0.45, 0.18, "• Import Lucid : coller le fichier .puml (PlantUML) ou importer ce PNG / le Mermaid .mmd", fontsize=7.2, color="#334155")

fig.tight_layout()
fig.savefig(out, bbox_inches="tight", facecolor=fig.get_facecolor())
fig.savefig(out2, bbox_inches="tight", facecolor=fig.get_facecolor())
print("OK", out, out.stat().st_size)
print("OK", out2)
