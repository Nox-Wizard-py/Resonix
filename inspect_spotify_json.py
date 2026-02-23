import re
import json
import sys

try:
    with open("spotify_real.html", "r", encoding="utf-8") as f:
        html = f.read()

    match = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.+?)</script>', html)
    if match:
        data = json.loads(match.group(1))
        print("Found JSON!")
        
        # Traverse to find tracks
        props = data.get("props", {})
        pageProps = props.get("pageProps", {})
        state = pageProps.get("state", {})
        data_inner = state.get("data", {})
        entity = data_inner.get("entity", {})
        
        print(f"Entity keys: {list(entity.keys())}")
        
        if "trackList" in entity:
            print("Found 'trackList'!")
            print(f"Type: {type(entity['trackList'])}")
            if isinstance(entity["trackList"], list):
                 print(f"Count: {len(entity['trackList'])}")
        
        if "tracks" in entity:
            print("Found 'tracks'!")
            tracks = entity["tracks"]
            if isinstance(tracks, dict):
                print(f"Tracks keys: {list(tracks.keys())}")
                if "items" in tracks:
                     print(f"Found 'tracks.items', count: {len(tracks['items'])}")
                     # Print first item structure
                     if len(tracks['items']) > 0:
                         print("First item keys:", tracks['items'][0].keys())
                         # Check title/name
                         first = tracks['items'][0]
                         print("Name:", first.get("name"))
                         print("Title:", first.get("title"))
                         print("Artists:", first.get("artists"))
                         if "track" in first:
                             print("Nested track keys:", first["track"].keys())
        
    else:
        print("No __NEXT_DATA__ found")
except Exception as e:
    print(f"Error: {e}")
