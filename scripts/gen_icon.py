from PIL import Image
import os

ROOT = r"C:\Users\Administrator\DoubaoWork\chats\2026-10-06\new-chat\Ace5Ultra-PerformanceKit"
SRC = os.path.join(ROOT, "design", "icon-source.jpg")
RES = os.path.join(ROOT, "app", "src", "main", "res")

BG = (11, 16, 32, 255)  # 0B1020

src = Image.open(SRC).convert("RGBA")

# densities: legacy launcher size (square), adaptive foreground canvas (108dp)
dens = {
    "mdpi":    (48, 108),
    "hdpi":    (72, 162),
    "xhdpi":   (96, 216),
    "xxhdpi":  (144, 324),
    "xxxhdpi": (192, 432),
}

def fit(img, box):
    w, h = img.size
    s = min(box[0]/w, box[1]/h)
    nw, nh = int(w*s), int(h*s)
    return img.resize((nw, nh), Image.LANCZOS)

for d, (legacy, fg) in dens.items():
    mdir = os.path.join(RES, f"mipmap-{d}")
    os.makedirs(mdir, exist_ok=True)

    # Adaptive foreground: 108dp canvas, source scaled to ~74% centered on BG.
    fg_img = Image.new("RGBA", (fg, fg), BG)
    inner = int(fg * 0.74)
    s = fit(src, (inner, inner))
    off = ((fg - s.size[0])//2, (fg - s.size[1])//2)
    fg_img.alpha_composite(s, off)
    fg_img.save(os.path.join(mdir, "ic_launcher_foreground.png"))

    # Legacy square: full-bleed source scaled to square.
    leg = fit(src, (legacy, legacy)).convert("RGB")
    leg.save(os.path.join(mdir, "ic_launcher.png"))
    leg.save(os.path.join(mdir, "ic_launcher_round.png"))

print("done")
