# -*- coding: utf-8 -*-
"""
Diagramme de classes HireLink CORRIGÉ — même template / couleurs que la maquette UML
(bleu entité · vert offre · beige domaine · violet enum) → PNG + PDF.
"""
from pathlib import Path
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle, FancyArrowPatch, Circle
from matplotlib.backends.backend_pdf import PdfPages

OUT_DIR = Path(r"C:\Users\daurinia\Desktop\HireLink\docs\diagrammes")
DESKTOP = Path(r"C:\Users\daurinia\Desktop")
PNG = OUT_DIR / "diagramme_classes_hirelink_CORRIGE.png"
PDF = DESKTOP / "HireLink_Diagramme_Classes.pdf"
PDF2 = OUT_DIR / "diagramme_classes_hirelink_CORRIGE.pdf"

# Couleurs template (comme maquette)
C_ENT = "#3B6EA5"      # bleu entité
C_OFF = "#3D8B5A"      # vert offre
C_DOM = "#C4A574"      # beige domaine métier
C_ENUM = "#7B6B9A"     # violet énumération
EDGE = "#1a1a1a"
INK = "#111827"
MUTED = "#64748b"

fig, ax = plt.subplots(figsize=(22, 14), dpi=180)
ax.set_xlim(0, 22)
ax.set_ylim(0, 14)
ax.axis("off")
fig.patch.set_facecolor("#ffffff")

ax.text(11, 13.55, "Diagramme UML de l'application HireLink",
        ha="center", fontsize=18, fontweight="bold", color=INK)
ax.text(11, 13.2, "Modèle JPA réel (corrigé) · Spring Boot · MySQL hirelink",
        ha="center", fontsize=9.5, color=MUTED)

# Légende
leg_y = 12.75
ax.add_patch(FancyBboxPatch((6.2, leg_y - 0.15), 9.6, 0.45, boxstyle="round,pad=0.02",
                            linewidth=0.8, edgecolor="#cbd5e1", facecolor="#f8fafc"))
for i, (col, lab) in enumerate([
    (C_ENT, "bleu = entité"),
    (C_OFF, "vert = offre"),
    (C_DOM, "beige = domaine métier"),
    (C_ENUM, "violet = énumération"),
]):
    x0 = 6.45 + i * 2.35
    ax.add_patch(Rectangle((x0, leg_y), 0.28, 0.18, facecolor=col, edgecolor=EDGE, lw=0.6))
    ax.text(x0 + 0.36, leg_y + 0.09, lab, ha="left", va="center", fontsize=7.2, color=INK)


def cls(x, y, w, h, name, attrs, header, stereotype="entité"):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0",
                                linewidth=1.15, edgecolor=EDGE, facecolor="white"))
    ax.add_patch(Rectangle((x, y + h - 0.52), w, 0.52, facecolor=header,
                           edgecolor=EDGE, linewidth=1.15))
    ax.text(x + w / 2, y + h - 0.16, f"«{stereotype}»", ha="center", va="center",
            fontsize=6.5, color="#e2e8f0", style="italic")
    ax.text(x + w / 2, y + h - 0.36, name, ha="center", va="center",
            fontsize=9.2, fontweight="bold", color="white")
    yy = y + h - 0.68
    for a in attrs:
        ax.text(x + 0.1, yy, a, ha="left", va="center", fontsize=6.4,
                color=INK, family="DejaVu Sans Mono")
        yy -= 0.20


def assoc(x1, y1, x2, y2, label="", m1="", m2="", color="#475569", ls="-"):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="-|>", color=color, lw=1.05,
                                mutation_scale=9, linestyle=ls))
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.12, label, ha="center", fontsize=6.2,
                color="#475569",
                bbox=dict(boxstyle="round,pad=0.1", fc="white", ec="none", alpha=0.95))
    if m1:
        ax.text(x1 + (x2 - x1) * 0.1, y1 + (y2 - y1) * 0.1 - 0.1, m1, fontsize=5.8, color=MUTED)
    if m2:
        ax.text(x2 - (x2 - x1) * 0.08, y2 - (y2 - y1) * 0.08 + 0.1, m2,
                fontsize=5.8, color=MUTED, ha="right")


def diamond_link(x1, y1, x2, y2, label=""):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="-|>", color="#475569", lw=1.05, mutation_scale=9))
    ax.plot(x1, y1, marker="D", markersize=6.5, color=EDGE)
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.1, label, ha="center", fontsize=6.2,
                color="#475569",
                bbox=dict(boxstyle="round,pad=0.08", fc="white", ec="none", alpha=0.95))


# ===== Rangée haut : identité =====
cls(0.25, 9.55, 3.55, 2.95, "User", [
    "- id: Long",
    "- email / passwordHash",
    "- fullName / firstName / lastName",
    "- role: Role",
    "- company / companyIce",
    "- companyVerified / verificationStatus",
    "- planTier: PlanTier",
    "- skills / soughtRoles / preferredContracts",
    "- cvFileName / cvStorageName / cvSections",
    "- googleSub  (pas de photo JPA)",
], C_ENT)

cls(4.05, 10.55, 2.85, 1.95, "Membership", [
    "- id: Long",
    "- orgRole: OrgRole",
    "- active: boolean",
    "- invitedAt / acceptedAt",
], C_ENT)

cls(7.15, 9.7, 3.45, 2.8, "Organization", [
    "- id / name / ice",
    "- verified / verificationStatus",
    "- verificationMethod",
    "- logoUrl / website",
    "- sector / city / employeeRange",
    "- presentation",
    "- planTier: PlanTier",
    "- owner → User",
], C_ENT)

cls(10.9, 11.15, 2.35, 1.35, "Role", [
    "CANDIDAT",
    "RECRUTEUR",
    "ADMINISTRATEUR",
], C_ENUM, stereotype="énumération")

cls(13.45, 11.15, 2.55, 1.35, "OrgRole", [
    "OWNER",
    "RECRUTEUR",
    "HIRING_MANAGER",
], C_ENUM, stereotype="énumération")

cls(16.2, 11.0, 2.7, 1.5, "PlanTier", [
    "ESSENTIEL",
    "PERFORMANCE",
    "ACCES",
    "PREMIUM",
], C_ENUM, stereotype="énumération")

cls(19.1, 10.85, 2.65, 1.65, "ApplicationStatus", [
    "SUBMITTED → SCREENING",
    "SHORTLISTED → INTERVIEW",
    "OFFER / ACCEPTED (déprécié)",
    "REJECTED / WITHDRAWN",
], C_ENUM, stereotype="énumération")

# ===== Offres (vert) =====
cls(0.25, 5.55, 3.7, 3.55, "JobOffer", [
    "- id: Long",
    "- title / description",
    "- requiredSkills: String",
    "- location / contractType",
    "- responseSlaDays: int",
    "- published: boolean",
    "- suspended / reportCount",
    "- quarantineReason",
    "- recruiter → User",
    "- createdAt: Instant",
], C_OFF, stereotype="offre")

cls(4.2, 7.35, 2.15, 1.55, "Tag", [
    "- id: Long",
    "- name: String",
    "N–N job_offer_tags",
], C_OFF, stereotype="offre")

cls(4.2, 5.55, 3.55, 1.6, "OfferReport", [
    "- jobOfferId: Long",
    "- reporterUserId: Long",
    "- reason: String",
    "- createdAt  « signalement »",
], C_OFF, stereotype="offre")

# ===== Domaine (beige) =====
cls(8.0, 5.35, 4.35, 3.85, "Application", [
    "- id: Long",
    "- candidate → User",
    "- jobOffer → JobOffer",
    "- status: ApplicationStatus",
    "- matchScore: Double",
    "- refusalReasonCode / rejectionReason",
    "- cvFileName / cvStorageName / cvSections",
    "- coverLetter / photoFileName",
    "- interviewType / interviewAt / location",
    "- assignedHmUserId / advanceNote",
    "- responseDeadline / slaBreachNotified",
    "- submittedAt / updatedAt",
], C_DOM, stereotype="domaine métier")

cls(12.6, 7.15, 3.55, 2.05, "ApplicationStatusHistory", [
    "- applicationId: Long",
    "- fromStatus / toStatus",
    "- actorUserId: Long",
    "- payloadJson: String",
    "- createdAt  « append-only »",
], C_DOM, stereotype="domaine métier")

cls(12.6, 5.35, 2.9, 1.55, "RefusalReason", [
    "- code: String",
    "- label: String",
    "- active: boolean",
], C_DOM, stereotype="domaine métier")

cls(15.7, 5.35, 3.0, 2.0, "MatchingAnalysis", [
    "- applicationId: Long",
    "- score: double",
    "- coveredSkills: String",
    "- missingSkills: String",
    "- createdAt: Instant",
], C_DOM, stereotype="domaine métier")

cls(18.95, 5.55, 2.8, 2.9, "ScorecardCriterion", [
    "- jobOfferId: Long",
    "- label: String",
    "- weight: int",
    "- veto: boolean",
], C_DOM, stereotype="domaine métier")

# ===== Bas : transversal =====
cls(0.25, 2.15, 3.2, 2.85, "ScorecardEntry", [
    "- applicationId: Long",
    "- criterionId: Long",
    "- evaluatorUserId: Long",
    "- score: int",
    "- comment: String",
    "- createdAt: Instant",
], C_DOM, stereotype="domaine métier")

cls(3.7, 2.35, 3.15, 2.65, "InAppNotification", [
    "- id / userId",
    "- title / body / kind",
    "- readFlag: boolean",
    "- createdAt: Instant",
], C_ENT)

cls(7.1, 2.35, 2.95, 2.65, "EmailOtp", [
    "- email / purpose",
    "- code: String",
    "- expiresAt",
    "- consumed: boolean",
], C_ENT)

cls(10.3, 2.35, 2.95, 2.65, "ConsentRecord", [
    "- userId: Long",
    "- purpose: String",
    "- granted: boolean",
    "- createdAt: Instant",
], C_ENT)

cls(13.5, 2.15, 3.55, 2.85, "AiUsageLog", [
    "- userId / organizationId",
    "- feature / provider / model",
    "- charsIn / charsOut",
    "- creditsSpent: int",
    "- success: boolean",
    "- createdAt: Instant",
], C_ENUM, stereotype="énumération / log")

cls(17.3, 2.35, 2.4, 2.65, "BillingInvoice", [
    "- number / org",
    "- planCode / amountTtc",
    "- status / issuedAt",
], C_ENT)

cls(19.9, 2.55, 1.85, 2.45, "CommercialPlan", [
    "- code / label",
    "- audience",
    "- monthlyMad",
    "- credits",
], C_ENT)

# ===== Associations =====
assoc(3.8, 11.2, 4.05, 11.4, "détient", "1", "0..*")
assoc(6.9, 11.4, 7.15, 11.3, "compte", "* ", "1")
assoc(2.0, 9.55, 2.0, 9.1, "crée / publie", "1", "0..*")
assoc(3.8, 10.2, 8.0, 8.5, "postule", "1", "0..*")
assoc(3.95, 7.5, 8.0, 7.8, "reçoit", "1", "0..*")
assoc(3.95, 8.5, 4.2, 8.5, "tagué", "0..*", "0..*")
assoc(2.0, 5.55, 4.2, 6.8, "signalée", "1", "0..*")
assoc(3.8, 10.0, 5.5, 7.15, "signale", "1", "0..*", ls="--")
diamond_link(12.35, 7.8, 12.6, 8.0, "historique")
assoc(12.35, 6.5, 12.6, 6.5, "refus", "0..*", "0..1")
assoc(12.35, 6.8, 15.7, 6.5, "analysée", "1", "0..1")
assoc(3.95, 6.2, 18.95, 7.5, "critères", "1", "0..*")
assoc(10.0, 5.35, 3.0, 5.0, "notes", "1", "0..*")
assoc(8.9, 9.7, 2.0, 9.55, "owner", "0..1", "1", ls="--")
assoc(10.9, 11.5, 3.8, 12.0, "", "1", "")
assoc(16.2, 11.3, 10.6, 11.8, "", "", "")
assoc(16.2, 11.0, 10.6, 11.0, "", "", "")

# Notes bas
ax.add_patch(FancyBboxPatch((0.25, 0.15), 21.5, 1.75, boxstyle="round,pad=0.04",
                            linewidth=0.9, edgecolor="#cbd5e1", facecolor="#f8fafc"))
ax.text(0.45, 1.6, "Notes d’alignement code (corrigé vs maquette)", fontsize=8.5,
        fontweight="bold", color=INK)
notes = [
    "• Membership = association N–N User ↔ Organization (OrgRole). Pas « User appartient à 1 Org » seul.",
    "• JobOffer.recruiter → User (pas de FK Organization). Logo / ICE / badge = Organization.logoUrl + verification*.",
    "• Application : matchScore seul (pas motivationScore / skillsScore / experienceYears). Entretien = interviewAt / interviewType.",
    "• MatchingAnalysis : coveredSkills / missingSkills. AiUsageLog : charsIn / charsOut (pas tokens). OfferReport = signalement.",
    "• Photo profil = locale (localStorage), pas de champ photo sur User. OFFER / ACCEPTED = dépréciés (pipeline actif → INTERVIEW).",
]
yy = 1.3
for n in notes:
    ax.text(0.45, yy, n, fontsize=6.6, color="#334155")
    yy -= 0.22

fig.tight_layout(pad=0.3)

OUT_DIR.mkdir(parents=True, exist_ok=True)
fig.savefig(PNG, bbox_inches="tight", facecolor="white")
print("PNG", PNG, PNG.stat().st_size)

for dest in (PDF, PDF2):
    with PdfPages(dest) as pdf:
        pdf.savefig(fig, bbox_inches="tight", facecolor="white")
    print("PDF", dest, dest.stat().st_size)

plt.close()
