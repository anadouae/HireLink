from pathlib import Path
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

out = Path(r"C:\Users\daurinia\Desktop\HireLink\docs\diagrammes\diagramme_classes_hirelink.png")
fig, ax = plt.subplots(figsize=(14, 9), dpi=160)
ax.set_xlim(0, 14); ax.set_ylim(0, 9); ax.axis("off"); fig.patch.set_facecolor("white")
ax.text(7, 8.6, "HireLink — Diagramme de classes (JPA / Hibernate)", ha="center", fontsize=16, fontweight="bold")

def box(x,y,w,h,title,lines,ec):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle="round,pad=0.02,rounding_size=0.1", linewidth=2, edgecolor=ec, facecolor="#fff"))
    ax.text(x+w/2, y+h-0.28, title, ha="center", fontsize=11, fontweight="bold", color=ec)
    for i,l in enumerate(lines):
        ax.text(x+0.15, y+h-0.65-i*0.28, l, ha="left", fontsize=8.5, color="#111")

box(0.4,5.5,3.0,2.5,"User",["id, email, role","@OneToMany offers","@OneToMany apps","@OneToMany memberships"],"#1d4ed8")
box(4.2,5.5,3.0,2.5,"JobOffer",["id, title, published","@ManyToOne recruiter","@OneToMany applications","@ManyToMany tags"],"#047857")
box(8.0,5.5,3.0,2.5,"Application",["id, status, score","@ManyToOne candidate","@ManyToOne jobOffer"],"#c2410c")
box(11.2,5.5,2.4,2.5,"Tag",["id, name","@ManyToMany offers"],"#7e22ce")
box(0.4,2.2,3.0,2.5,"Organization",["id, name, ice","@ManyToOne owner","@OneToMany memberships"],"#0f766e")
box(4.2,2.2,3.0,2.5,"Membership",["id, orgRole","@ManyToOne user","@ManyToOne organization","= N-N User↔Org"],"#b45309")
box(8.0,2.2,3.0,2.5,"RefusalReason",["id, code, label","CRUD admin"],"#b91c1c")
box(11.2,2.2,2.4,2.5,"Notification",["id, userId","title, read","CRUD user"],"#0369a1")

def arr(x1,y1,x2,y2,txt=""):
    ax.annotate("", xy=(x2,y2), xytext=(x1,y1), arrowprops=dict(arrowstyle="-|>", color="#334155", lw=1.6, mutation_scale=12))
    if txt: ax.text((x1+x2)/2, (y1+y2)/2+0.12, txt, fontsize=7, color="#475569", ha="center")

arr(3.4,6.7,4.2,6.7,"1—*")
arr(7.2,6.7,8.0,6.7,"1—*")
arr(11.0,6.5,11.2,6.5,"*—*")
arr(3.4,3.4,4.2,3.4,"1—*")
arr(1.9,5.5,1.9,4.7,"")
arr(5.7,5.5,5.7,4.7,"")

ax.text(7, 0.55, "@ManyToOne / @OneToMany / @ManyToMany — persistance MySQL (ddl-auto=update)", ha="center", fontsize=10, color="#334155")
fig.tight_layout(); fig.savefig(out, bbox_inches="tight", facecolor="white"); print(out, out.stat().st_size)
