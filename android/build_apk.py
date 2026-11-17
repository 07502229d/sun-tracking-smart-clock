#!/usr/bin/env python3
"""Build the Sun Tracking Smart Clock Android APK (WebView wrapper around the
static app) with aapt2/javac/d8 directly — no Gradle. Adapted from the proven
Liu Shu pipeline (liu-shu-web/android/build_apks.py) and signed with the same
Liu Shu release keystore, which lives outside this folder."""
import hashlib, math, os, shutil, subprocess, sys, zipfile
from PIL import Image, ImageDraw

HOME = os.path.expanduser("~")
JAVA = f"{HOME}/workspace/tools/jdk-17/bin"
SDK = f"{HOME}/workspace/tools/android-sdk"
BT = f"{SDK}/build-tools/34.0.0"
JAR = f"{SDK}/platforms/android-34/android.jar"
ROOT = f"{HOME}/workspace/sun-tracking-android"
KEYS = f"{HOME}/workspace/liu-shu-keys"
HTML_IN = f"{HOME}/workspace/your_files/sun-tracking-smart-clock/sun-tracking-smart-clock.html"
OUT = f"{HOME}/workspace/your_files/sun-tracking-smart-clock/sun-tracking-smart-clock-v1.2.apk"

PACKAGE = "hk.suntracking.clock"
LABEL = "Sun Tracking Smart Clock"
VERSION_CODE = 3
VERSION_NAME = "1.2"
BG = "#0d2b52"      # deep sky navy, matches the app theme
GOLD = "#c9a227"
SUN = "#ffd34d"

ENV = dict(os.environ)
ENV["PATH"] = f"{HOME}/workspace/tools/jdk-17/bin:" + ENV.get("PATH", "")
ENV["JAVA_HOME"] = f"{HOME}/workspace/tools/jdk-17"

def run(cmd, **kw):
    kw.setdefault("env", ENV)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        print("FAILED:", " ".join(cmd)); print(r.stdout[-1500:]); print(r.stderr[-1500:]); sys.exit(1)
    return r

def make_icons(outdir):
    """Sun-themed launcher icons, drawn the same PIL way as the Liu Shu build."""
    dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, size in dens.items():
        md = os.path.join(outdir, f"mipmap-{d}")
        os.makedirs(md, exist_ok=True)
        img = Image.new("RGB", (size, size), BG)
        dr = ImageDraw.Draw(img)
        dr.rounded_rectangle([size*0.05, size*0.05, size*0.95, size*0.95],
                             radius=size*0.14, outline=GOLD, width=max(2, size//32))
        cx = cy = size / 2
        r_core = size * 0.17
        r_in, r_out = size * 0.235, size * 0.335
        w = max(2, size // 22)
        for k in range(8):
            a = math.radians(45 * k)
            dr.line([cx + r_in * math.cos(a), cy + r_in * math.sin(a),
                     cx + r_out * math.cos(a), cy + r_out * math.sin(a)],
                    fill=SUN, width=w)
        dr.ellipse([cx - r_core, cy - r_core, cx + r_core, cy + r_core], fill=SUN)
        img.save(os.path.join(md, "ic_launcher.png"))

def main():
    ks = os.path.join(KEYS, "liushu-release.keystore")
    pwfile = os.path.join(KEYS, "keystore-password.txt")
    if not os.path.exists(ks) or not os.path.exists(pwfile):
        print("ERROR: Liu Shu release keystore/password not found in", KEYS)
        print("Refusing to create a new keystore: the APK must be signed with")
        print("the same certificate as the Liu Shu apps.")
        sys.exit(1)
    pw = open(pwfile).read().strip()
    if not os.path.exists(HTML_IN):
        print("ERROR: input HTML not found:", HTML_IN); sys.exit(1)

    work = os.path.join(ROOT, "build")
    shutil.rmtree(work, ignore_errors=True)
    for sub in ("assets", "res", "src", "classes", "dex"):
        os.makedirs(os.path.join(work, sub))
    shutil.copy(HTML_IN, os.path.join(work, "assets", "index.html"))

    manifest = open(os.path.join(ROOT, "AndroidManifest.xml"), encoding="utf-8").read()
    manifest = (manifest.replace("__PACKAGE__", PACKAGE).replace("__LABEL__", LABEL)
                .replace("__VCODE__", str(VERSION_CODE)).replace("__VNAME__", VERSION_NAME))
    mpath = os.path.join(work, "AndroidManifest.xml")
    open(mpath, "w", encoding="utf-8").write(manifest)

    jsrc = open(os.path.join(ROOT, "src", "MainActivity.java"), encoding="utf-8").read().replace("__PACKAGE__", PACKAGE)
    jpath = os.path.join(work, "src", "MainActivity.java")
    open(jpath, "w", encoding="utf-8").write(jsrc)

    make_icons(os.path.join(work, "res"))
    run([f"{BT}/aapt2", "compile", "--dir", os.path.join(work, "res"), "-o", os.path.join(work, "res.zip")])
    unsigned = os.path.join(work, "unsigned.apk")
    run([f"{BT}/aapt2", "link", "-o", unsigned, "-I", JAR, "--manifest", mpath,
         "-A", os.path.join(work, "assets"), os.path.join(work, "res.zip")])
    run([f"{JAVA}/javac", "-cp", JAR, "-d", os.path.join(work, "classes"), jpath])
    run([f"{BT}/d8", "--output", os.path.join(work, "dex"),
         *[os.path.join(dp, f) for dp, _, fs in os.walk(os.path.join(work, "classes")) for f in fs if f.endswith(".class")]])
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(work, "dex", "classes.dex"), "classes.dex")
    aligned = os.path.join(work, "aligned.apk")
    run([f"{BT}/zipalign", "-f", "4", unsigned, aligned])
    run([f"{BT}/apksigner", "sign", "--ks", ks, "--ks-pass", f"pass:{pw}", "--key-pass", f"pass:{pw}",
         "--out", OUT, aligned])
    print(f"BUILT {OUT} ({os.path.getsize(OUT):,} bytes)")

if __name__ == "__main__":
    main()
