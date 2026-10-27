# -*- coding: utf-8 -*-
"""
Diagramme de classes HireLink — style CDC (comme maquette UML fournie)
contenu à jour : anti-fraude LinkedIn-like, SLA, signalements, IA.
"""
from pathlib import Path
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle, FancyArrowPatch
from matplotlib.lines import Line2D

OUT_DIR = Path(r"C:\Users\daurinia\Desktop\HireLink\docs\diagrammes")
out = OUT_DIR / "diagramme_classes_hirelink_FINAL.png"
out2 = OUT_DIR / "diagramme_classes_hirelink.png"
out_cdc = OUT_DIR / "diagramme_classes_hirelink_CDC_STYLE.png"

fig, ax = plt.subplots(figsize=(20, 13.5), dpi=170)
ax.set_xlim(0, 20)
ax.set_ylim(0, 13.5)
ax.axis("off")
fig.patch.set_facecolor("#ffffff")

ax.text(10, 13.15, "HireLink — Diagramme de classes (modèle métier à jour)",
        ha="center", fontsize=16, fontweight="bold", color="#0f172a")
ax.text(10, 12.8, "Style UML CDC · Anti-fraude LinkedIn-like · Pipeline ATS · Crédits IA",
        ha="center", fontsize=9.5, color="#64748b")

BLUE = "#1e3a5f"
BROWN = "#8B4513"
GREEN = "#166534"
EDGE = "#0f172a"


def cls(x, y, w, h, name, stereotype, attrs, methods, header=BLUE):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0",
                                linewidth=1.4, edgecolor=EDGE, facecolor="white"))
    ax.add_patch(Rectangle((x, y + h - 0.48), w, 0.48, facecolor=header,
                           edgecolor=EDGE, linewidth=1.4))
    ax.text(x + w / 2, y + h - 0.16, f"«{stereotype}»", ha="center", va="center",
            fontsize=7, color="#cbd5e1", style="italic")
    ax.text(x + w / 2, y + h - 0.36, name, ha="center", va="center",
            fontsize=10, fontweight="bold", color="white")
    # attribute / method split
    n_attr = len(attrs)
    split = y + h - 0.48 - 0.22 * n_attr - 0.12
    ax.plot([x, x + w], [split, split], color="#94a3b8", lw=0.9)
    yy = y + h - 0.62
    for a in attrs:
        ax.text(x + 0.1, yy, a, ha="left", va="center", fontsize=7.2,
                color="#0f172a", family="DejaVu Sans Mono")
        yy -= 0.22
    yy = split - 0.16
    for m in methods:
        ax.text(x + 0.1, yy, m, ha="left", va="center", fontsize=7.2,
                color="#1e40af", family="DejaVu Sans Mono")
        yy -= 0.22


def assoc(x1, y1, x2, y2, label="", m1="", m2="", style="-|>", color="#334155", ls="-"):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle=style, color=color, lw=1.25,
                                mutation_scale=11, linestyle=ls))
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.14, label, ha="center", fontsize=7,
                color="#475569",
                bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.95))
    if m1:
        ax.text(x1 + (x2 - x1) * 0.12, y1 + (y2 - y1) * 0.12 - 0.12, m1,
                fontsize=6.5, color="#64748b")
    if m2:
        ax.text(x2 - (x2 - x1) * 0.12, y2 - (y2 - y1) * 0.12 + 0.12, m2,
                fontsize=6.5, color="#64748b", ha="right")


def diamond(x1, y1, x2, y2, label=""):
    """Composition (filled diamond at x1)."""
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="-|>", color="#334155", lw=1.2, mutation_scale=10))
    # small diamond marker near source
    ax.plot(x1, y1, marker="D", markersize=7, color="#0f172a")
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2 + 0.12, label, ha="center", fontsize=7,
                color="#475569",
                bbox=dict(boxstyle="round,pad=0.1", fc="white", ec="none", alpha=0.95))


# ===== Layout matching CDC image =====
# Top-left: User
cls(0.3, 9.6, 3.2, 2.7, "User", "class",
    ["- id: Long", "- email: String", "- passwordHash: String",
     "- role: Role", "- company / companyIce",
     "- companyVerified: boolean", "- verificationStatus: String",
     "- planTier: PlanTier", "- createdAt: Instant"],
    ["+ login()", "+ updateProfile()"], BLUE)

# Top: CandidateProfile (conceptuel — attributs dans User en code)
cls(4.0, 10.3, 3.3, 2.2, "CandidateProfile", "class",
    ["- skills: String", "- soughtRoles: String",
     "- preferredLocations / contracts", "- experienceLevel",
     "- profileComplete: boolean"],
    ["+ updateProfile()", "+ exportData()"], BLUE)

# Top-right: Organization
cls(11.5, 10.0, 3.6, 2.5, "Organization", "class",
    ["- id: Long", "- name: String", "- ice: String",
     "- verified: boolean", "- verificationStatus: String",
     "- planTier: PlanTier"],
    ["+ verify()"], BLUE)

# Membership
cls(7.8, 10.5, 3.2, 2.0, "Membership", "class",
    ["- orgRole: OrgRole", "- active: boolean",
     "- invitedAt / acceptedAt"],
    ["+ hasPermission(action)"], BLUE)

# JobOffer (updated antifraud)
cls(15.5, 9.5, 4.1, 3.0, "JobOffer", "class",
    ["- title: String", "- requiredSkills: String",
     "- responseSlaDays: int", "- published: boolean",
     "- suspended: boolean", "- reportCount: int",
     "- quarantineReason: String", "- createdAt: Instant"],
    ["+ publish()", "+ close()", "+ suspend()"], BLUE)

# Application (center brown)
cls(6.5, 5.8, 4.0, 2.9, "Application", "class",
    ["- status: ApplicationStatus", "- matchScore: Double",
     "- refusalReasonCode: String", "- appliedAt: Instant",
     "- responseDeadline: Instant", "- slaBreachNotified: boolean",
     "- assignedHmUserId: Long", "- advanceNote: String"],
    ["+ transitionTo(status)", "+ withdraw()"], BROWN)

# Interview (conceptuel — champs dans Application)
cls(4.0, 3.5, 3.0, 1.9, "Interview", "class",
    ["- type: String", "- scheduledAt: Instant",
     "- location: String", "- notes: String"],
    ["+ schedule()"], BLUE)

# OfferReport (NEW antifraud)
cls(15.5, 6.8, 4.1, 2.2, "OfferReport", "class",
    ["- jobOfferId: Long", "- reporterUserId: Long",
     "- reason: String", "- createdAt: Instant"],
    ["+ 3 reports → suspend offer"], BROWN)

# ApplicationStatusHistory
cls(11.0, 5.5, 3.8, 2.2, "ApplicationStatusHistory", "class",
    ["- fromStatus / toStatus", "- actorUserId: Long",
     "- payloadJson: String", "- occurredAt: Instant"],
    ["« ajout seul, jamais modifié »"], BLUE)

# RefusalReason
cls(11.0, 3.3, 3.2, 1.8, "RefusalReason", "class",
    ["- code: String", "- label: String", "- active: boolean"],
    [], BLUE)

# Scorecard
cls(0.3, 5.5, 3.3, 2.2, "ScorecardCriterion", "class",
    ["- label: String", "- weight: int", "- canVeto: boolean"],
    [], BLUE)
cls(0.3, 3.2, 3.3, 2.0, "ScorecardEntry", "class",
    ["- score: int", "- veto: boolean", "- comment: String"],
    ["+ submit()"], BLUE)

# MatchingAnalysis green
cls(14.8, 3.5, 4.8, 2.5, "MatchingAnalysis", "class",
    ["- score: Double", "- coveredSkills: String",
     "- missingSkills: String", "- createdAt: Instant"],
    ["+ compute()  « F14 local Jaccard »"], GREEN)

# Bottom green: Consent, AiCreditLedger, AiUsageLog
cls(0.3, 0.35, 3.5, 2.3, "ConsentRecord", "class",
    ["- purpose: String", "- granted: boolean",
     "- createdAt: Instant"],
    [], GREEN)

cls(4.2, 0.35, 4.0, 2.5, "AiCreditLedger", "class",
    ["- plan: PlanTier", "- monthlyQuota: int",
     "- balance: int", "- period: YearMonth"],
    ["+ hasCredit()", "+ debit()", "+ resetMonthly()"], GREEN)

cls(8.6, 0.35, 5.2, 2.5, "AiUsageLog", "class",
    ["- feature / provider / model", "- charsIn / charsOut",
     "- creditsSpent: int", "- success: boolean",
     "- createdAt: Instant"],
    ["+ R16 : débit seulement si succès"], GREEN)

cls(14.2, 0.35, 5.4, 2.3, "EmailOtp / Tag / InAppNotification", "class",
    ["EmailOtp: code, purpose, expires",
     "Tag: name  « N–N job_offer_tags »",
     "InAppNotification: title, kind, read"],
    [], GREEN)

# ===== Associations (CDC style) =====
assoc(3.5, 11.0, 4.0, 11.2, "possède", "1", "0..1")
assoc(2.0, 9.6, 2.0, 7.7, "donne", "1", "0..*")  # toward Consent area - adjust
# User -> Membership
assoc(3.5, 10.5, 7.8, 11.2, "détient", "1", "0..*")
# Membership -> Organization
assoc(11.0, 11.3, 11.5, 11.3, "compte", "1..*", "1")
# Organization -> JobOffer
assoc(15.1, 11.2, 15.5, 11.2, "publie", "1", "0..*")
# CandidateProfile -> Application
assoc(5.6, 10.3, 7.5, 8.7, "dépose", "1", "0..*")
# JobOffer -> Application
assoc(15.5, 10.0, 10.5, 8.0, "reçoit", "1", "0..*")
# Membership -> Application (HM)
assoc(9.5, 10.5, 8.5, 8.7, "assigné HM", "0..*", "0..*")
# Application composition History
diamond(10.5, 7.0, 11.0, 6.8, "historique")
# Application -> Interview
assoc(7.5, 5.8, 6.0, 5.4, "génère", "1", "0..*")
# Application -> RefusalReason
assoc(10.5, 6.5, 11.5, 5.1, "refusée pour", "0..*", "0..1")
# Application -> MatchingAnalysis
assoc(10.5, 6.8, 14.8, 5.2, "analysée par", "1", "0..*")
# JobOffer -> ScorecardCriterion
assoc(15.5, 9.5, 3.6, 7.0, "définit", "1", "1..*")
# Interview -> ScorecardEntry
assoc(4.0, 4.2, 3.6, 4.5, "produit", "1", "0..*")
# JobOffer -> OfferReport
assoc(17.5, 9.5, 17.5, 9.0, "signalée", "1", "0..*")
# User -> OfferReport (reporter) - dashed conceptually
assoc(3.5, 10.0, 15.5, 8.0, "signale", "1", "0..*", ls="--")
# Organization / User -> AiCreditLedger
assoc(13.0, 10.0, 7.5, 2.85, "dispose de", "0..1", "0..1", ls="--")
assoc(2.0, 9.6, 5.5, 2.85, "dispose de", "0..1", "0..1", ls="--")
# AiCreditLedger -> AiUsageLog
assoc(8.2, 1.2, 8.6, 1.2, "trace", "1", "0..*")
# JobOffer -> Matching (already) / User consent
assoc(1.8, 9.6, 1.8, 2.65, "consent", "1", "0..*")

# Note box
ax.add_patch(FancyBboxPatch((14.2, 0.15), 5.5, 0.15, boxstyle="square,pad=0",
                            linewidth=0, facecolor="none"))
note = (
    "Notes d'implémentation : CandidateProfile & Interview & AiCreditLedger = "
    "concepts CDC ; en code → attributs User / Application / planTier+AiUsageLog. "
    "Anti-fraude : NEED_CORPORATE_EMAIL · publish si companyVerified · OfferReport≥3 → suspended."
)
ax.text(10, 0.12, note, ha="center", va="bottom", fontsize=6.8, color="#64748b",
        wrap=True)

fig.tight_layout()
paths = [
    out_cdc,
    OUT_DIR / "diagramme_classes_hirelink_FINAL_v2.png",
    out,
    out2,
]
for path in paths:
    try:
        fig.savefig(path, bbox_inches="tight", facecolor="white")
        print("OK", path, path.stat().st_size)
    except OSError as e:
        print("SKIP", path.name, e)
