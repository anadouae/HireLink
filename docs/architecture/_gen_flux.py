from pathlib import Path
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Circle

out = Path(r"C:\Users\daurinia\Desktop\HireLink\docs\architecture\HireLink_Architecture_Couches.png")

fig, ax = plt.subplots(figsize=(12, 7.2), dpi=180)
ax.set_xlim(0, 12)
ax.set_ylim(0, 7.2)
ax.axis("off")
fig.patch.set_facecolor("white")

ax.text(6, 6.85, "HireLink — Schema Controller / Service / Repository",
        ha="center", fontsize=17, fontweight="bold", color="#111827")

def big_box(x, y, w, h, title, line1, line2, fc, ec):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.04,rounding_size=0.2",
                                linewidth=3, edgecolor=ec, facecolor=fc))
    ax.text(x + w/2, y + h*0.68, title, ha="center", va="center",
            fontsize=16, fontweight="bold", color=ec)
    ax.text(x + w/2, y + h*0.38, line1, ha="center", va="center", fontsize=11, color="#1f2937")
    ax.text(x + w/2, y + h*0.18, line2, ha="center", va="center", fontsize=10, color="#4b5563")

# Three main boxes horizontal - EXACT deliverable
big_box(0.5, 3.6, 3.2, 2.2, "CONTROLLER", "Recoit la requete HTTP", "Ex: JobOfferController", "#dcfce7", "#166534")
big_box(4.4, 3.6, 3.2, 2.2, "SERVICE", "Logique metier", "Ex: JobOfferService", "#ffedd5", "#9a3412")
big_box(8.3, 3.6, 3.2, 2.2, "REPOSITORY", "Acces a la base", "Ex: JobOfferRepository", "#ede9fe", "#5b21b6")

# Big arrows between them
ax.annotate("", xy=(4.35, 4.7), xytext=(3.75, 4.7),
            arrowprops=dict(arrowstyle="-|>", color="#111827", lw=4, mutation_scale=22))
ax.annotate("", xy=(8.25, 4.7), xytext=(7.65, 4.7),
            arrowprops=dict(arrowstyle="-|>", color="#111827", lw=4, mutation_scale=22))

ax.text(4.05, 5.15, "appelle", ha="center", fontsize=10, color="#374151")
ax.text(7.95, 5.15, "appelle", ha="center", fontsize=10, color="#374151")

# Injection arrows ABOVE (dashed) from Spring
ax.add_patch(FancyBboxPatch((3.5, 6.0), 5.0, 0.55, boxstyle="round,pad=0.02,rounding_size=0.12",
                            linewidth=2, edgecolor="#0369a1", facecolor="#e0f2fe"))
ax.text(6.0, 6.28, "SPRING  (IoC)  injecte les dependances", ha="center", va="center",
        fontsize=11, fontweight="bold", color="#0369a1")

# dashed injection arrows down
ax.annotate("", xy=(6.0, 5.85), xytext=(6.0, 6.0),
            arrowprops=dict(arrowstyle="-|>", color="#0369a1", lw=2, linestyle="--", mutation_scale=12))
ax.annotate("", xy=(2.1, 5.85), xytext=(4.2, 6.0),
            arrowprops=dict(arrowstyle="-|>", color="#0369a1", lw=2, linestyle="--", mutation_scale=12))
ax.annotate("", xy=(9.9, 5.85), xytext=(7.8, 6.0),
            arrowprops=dict(arrowstyle="-|>", color="#0369a1", lw=2, linestyle="--", mutation_scale=12))

# DB below repository
big_box(8.3, 0.7, 3.2, 1.8, "BASE MySQL", "table job_offers", "base hirelink", "#f3f4f6", "#111827")
ax.annotate("", xy=(9.9, 2.55), xytext=(9.9, 3.55),
            arrowprops=dict(arrowstyle="-|>", color="#111827", lw=3.5, mutation_scale=18))
ax.text(10.5, 3.05, "lit / ecrit", ha="left", fontsize=10, color="#374151")

# Entity small
big_box(4.4, 0.7, 3.2, 1.8, "ENTITY", "JobOffer.java", "@Entity = table", "#fee2e2", "#991b1b")
ax.annotate("", xy=(7.55, 1.6), xytext=(8.25, 1.6),
            arrowprops=dict(arrowstyle="<|-|>", color="#6b7280", lw=2, mutation_scale=12))

# Legend bottom left - injection explained in 1 line
ax.add_patch(FancyBboxPatch((0.5, 0.7), 3.5, 1.8, boxstyle="round,pad=0.04,rounding_size=0.15",
                            linewidth=2, edgecolor="#0369a1", facecolor="#f0f9ff"))
ax.text(2.25, 2.15, "Injection =", ha="center", fontsize=12, fontweight="bold", color="#0369a1")
ax.text(2.25, 1.65, "Spring donne automatiquement", ha="center", fontsize=9.5, color="#0f172a")
ax.text(2.25, 1.30, "le Service au Controller", ha="center", fontsize=9.5, color="#0f172a")
ax.text(2.25, 0.95, "(sans ecrire new ...)", ha="center", fontsize=9.5, color="#0f172a")

fig.tight_layout()
fig.savefig(out, bbox_inches="tight", facecolor="white")
print("OK", out, out.stat().st_size)
