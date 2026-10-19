#!/usr/bin/env python3
"""Meteo benchmark: 30 deterministic scenes, screenshots and reproducible metrics.

Install: pip install playwright pillow; python -m playwright install chromium
Run: python tools/sky_benchmark.py --html app/src/main/assets/sky.html --out benchmark/current
Compare: python tools/sky_benchmark.py --html ... --out benchmark/new --baseline benchmark/current/metrics.json
"""
import argparse, json, math, statistics
from datetime import datetime
from pathlib import Path
from urllib.parse import urlencode
from PIL import Image
from playwright.sync_api import sync_playwright

WEATHER = ("sereno", "poco", "nuvoloso", "coperto", "temporale", "nebbia")
HOURS = (7, 11, 15, 18, 23)
DATE = "2026-10-19"

def metrics(path):
    im = Image.open(path).convert("RGB")
    im.thumbnail((360, 640))
    px = list(im.getdata())
    n = len(px)
    lums = [(0.2126*r + 0.7152*g + 0.0722*b) for r,g,b in px]
    blues = [b > r*1.12 and b > g*1.025 and b-r > 13 and b > 55 for r,g,b in px]
    w,h = im.size
    # Spatially local luminance contrast, rather than only global stddev.
    blocks = []
    for y in range(0,h-7,8):
        for x in range(0,w-7,8):
            vals = [lums[yy*w+xx] for yy in range(y,y+8) for xx in range(x,x+8)]
            blocks.append(statistics.pstdev(vals))
    # Coverage by image thirds exposes 'all gray above, blue only at bottom'.
    thirds = []
    for k in range(3):
        lo = k*h//3; hi=(k+1)*h//3
        thirds.append(round(100*sum(blues[lo*w:hi*w])/((hi-lo)*w),2))
    return dict(mean_luma=round(statistics.mean(lums),2),
                blue_pct=round(100*sum(blues)/n,2),
                contrast=round(statistics.pstdev(lums),2),
                local_contrast=round(statistics.mean(blocks),2),
                blue_by_third_pct=thirds)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--html", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--baseline")
    ap.add_argument("--references", help="Directory with numbered 01..18 reference JPG/PNG images")
    ap.add_argument("--wait-ms",type=int,default=3500)
    args=ap.parse_args()
    output=Path(args.out); output.mkdir(parents=True,exist_ok=True)
    source=Path(args.html).resolve().as_uri()
    report={"source":str(Path(args.html)), "scenarios":{}, "failures":[]}
    with sync_playwright() as p:
        browser=p.chromium.launch(headless=True,args=["--enable-webgl","--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader"])
        page=browser.new_page(viewport={"width":393,"height":852},device_scale_factor=1)
        errors=[]
        page.on("pageerror",lambda exc: errors.append(str(exc)))
        for weather in WEATHER:
            for hour in HOURS:
                key=f"{weather}_{hour:02d}"
                date=f"{DATE}T{hour:02d}:00:00+02:00"
                url=source+"?"+urlencode({"benchDate":date,"benchWeather":weather,"lat":"45.47","lon":"9.19"})
                errors.clear()
                try:
                    page.goto(url,wait_until="load",timeout=30000)
                    page.wait_for_timeout(args.wait_ms)
                    shot=output/(key+".png")
                    page.screenshot(path=str(shot))
                    report["scenarios"][key]=metrics(shot)
                    if errors: report["failures"].append({"scene":key,"errors":list(errors)})
                except Exception as exc:
                    report["failures"].append({"scene":key,"errors":[str(exc)]})
        browser.close()
    # Generated reference images are visual targets, not meteorological ground truth.
    # Match comparable scenarios only: other hours/conditions are not silently extrapolated.
    reference_map = {
        "01":"sereno_11", "02":"poco_15", "03":"nuvoloso_11",
        "04":"nuvoloso_15", "05":"coperto_11", "09":"temporale_15",
        "11":"nebbia_07", "13":"sereno_18", "14":"nuvoloso_18",
        "16":"sereno_23", "17":"coperto_23",
    }
    if args.references:
        folder=Path(args.references)
        report["reference_comparison"]={}
        for number,scene in reference_map.items():
            matches=sorted(p for p in folder.glob(number+"*") if p.suffix.lower() in (".jpg",".jpeg",".png",".webp"))
            if not matches or scene not in report["scenarios"]:
                continue
            ref=metrics(matches[0]); actual=report["scenarios"][scene]
            report["reference_comparison"][number]={
                "file":str(matches[0]),"scene":scene,"reference_metrics":ref,
                "delta":{k:round(actual[k]-ref[k],2) for k in ("mean_luma","blue_pct","contrast","local_contrast")},
                "note":"Visual comparison only; synthetic reference does not validate weather physics."
            }
    if args.baseline:
        old=json.loads(Path(args.baseline).read_text(encoding="utf-8")).get("scenarios",{})
        report["changes_from_baseline"]={}
        for key,values in report["scenarios"].items():
            if key in old:
                report["changes_from_baseline"][key]={k:round(values[k]-old[key][k],2) for k in ("mean_luma","blue_pct","contrast","local_contrast")}
    (output/"metrics.json").write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf-8")
    print(f"Captured {len(report['scenarios'])}/30 scenes; failures: {len(report['failures'])}; output: {output}")
    if report["failures"]: print(json.dumps(report["failures"],indent=2))

if __name__=="__main__": main()
