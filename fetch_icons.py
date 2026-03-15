import urllib.request
import re
import os

icons = [
    "settings", "wand-2", "palette", "layout", "speaker", "volume-2", 
    "headphones", "list-music", "arrow-down-to-line", "globe", "puzzle", 
    "history", "shield", "lock", "download", "hard-drive", "database-backup", 
    "refresh-cw", "sparkles", "info", "chevron-right", "monitor-smartphone"
]

out_dir = r"c:\Users\admin\OneDrive\Documents\Nox Tune\app\src\main\res\drawable"
os.makedirs(out_dir, exist_ok=True)

def circle_to_path(cx, cy, r):
    return f"M {cx} {cy - r} a {r},{r} 0 1,0 0,{2*r} a {r},{r} 0 1,0 0,{-2*r}"

def rect_to_path(x, y, w, h, rx=0):
    if rx == 0:
        return f"M {x} {y} H {x+w} V {y+h} H {x} Z"
    else:
        return f"M {x+rx} {y} H {x+w-rx} A {rx} {rx} 0 0 1 {x+w} {y+rx} V {y+h-rx} A {rx} {rx} 0 0 1 {x+w-rx} {y+h} H {x+rx} A {rx} {rx} 0 0 1 {x} {y+h-rx} V {y+rx} A {rx} {rx} 0 0 1 {x+rx} {y} Z"

def line_to_path(x1, y1, x2, y2):
    return f"M {x1} {y1} L {x2} {y2}"

def poly_to_path(points, is_polygon):
    pts = re.split(r'[ ,]+', points.strip())
    # Handle alternating x y or combined "x,y"
    parsed_pts = []
    for p in pts:
        if ',' in p:
            parsed_pts.append(p.replace(',', ' '))
        else:
            parsed_pts.append(p)
    path = f"M {parsed_pts[0]} {parsed_pts[1]}" if len(parsed_pts) > 1 else ""
    if path:
        i = 2
        while i < len(parsed_pts) - 1:
            path += f" L {parsed_pts[i]} {parsed_pts[i+1]}"
            i += 2
        if is_polygon:
            path += " Z"
    return path

for icon in icons:
    url = f"https://unpkg.com/lucide-static@latest/icons/{icon}.svg"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            svg = response.read().decode('utf-8')

        # Extract inner tags
        paths = []
        
        # Paths
        for m in re.finditer(r'<path[^>]*d="([^"]+)"', svg):
            paths.append(m.group(1))
            
        # Circles
        for m in re.finditer(r'<circle([^>]+)>', svg):
            attrs = m.group(1)
            cx = float(re.search(r'cx="([\d\.]+)"', attrs).group(1))
            cy = float(re.search(r'cy="([\d\.]+)"', attrs).group(1))
            r = float(re.search(r'r="([\d\.]+)"', attrs).group(1))
            paths.append(circle_to_path(cx, cy, r))

        # Rects
        for m in re.finditer(r'<rect([^>]+)>', svg):
            attrs = m.group(1)
            x_m = re.search(r'\bx="([\d\.\-]+)"', attrs)
            y_m = re.search(r'\by="([\d\.\-]+)"', attrs)
            w = float(re.search(r'width="([\d\.]+)"', attrs).group(1))
            h = float(re.search(r'height="([\d\.]+)"', attrs).group(1))
            x = float(x_m.group(1)) if x_m else 0.0
            y = float(y_m.group(1)) if y_m else 0.0
            rx_m = re.search(r'rx="([\d\.]+)"', attrs)
            rx = float(rx_m.group(1)) if rx_m else 0.0
            paths.append(rect_to_path(x, y, w, h, rx))

        # Lines
        for m in re.finditer(r'<line([^>]+)>', svg):
            attrs = m.group(1)
            x1 = float(re.search(r'x1="([\d\.\-]+)"', attrs).group(1))
            y1 = float(re.search(r'y1="([\d\.\-]+)"', attrs).group(1))
            x2 = float(re.search(r'x2="([\d\.\-]+)"', attrs).group(1))
            y2 = float(re.search(r'y2="([\d\.\-]+)"', attrs).group(1))
            paths.append(line_to_path(x1, y1, x2, y2))
            
        # Polylines
        for m in re.finditer(r'<polyline([^>]+points="([^"]+)")', svg):
            pts = m.group(2)
            paths.append(poly_to_path(pts, False))
            
        # Polygons
        for m in re.finditer(r'<polygon([^>]+points="([^"]+)")', svg):
            pts = m.group(2)
            paths.append(poly_to_path(pts, True))

        path_elements = ""
        for p in paths:
            path_elements += f"""
    <path
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{p}" />"""

        xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0"
    android:tint="?attr/colorControlNormal">{path_elements}
</vector>"""
        
        filename = f"lucide_{icon.replace('-', '_')}.xml"
        filepath = os.path.join(out_dir, filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(xml_content)
        print(f"Created {filename}")
        
    except Exception as e:
        print(f"Failed {icon}: {e}")

print("Done")
