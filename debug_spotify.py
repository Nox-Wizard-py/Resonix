import requests
import re
import json

def debug_spotify_url(url):
    playlist_id_match = re.search(r'spotify\.com/playlist/([a-zA-Z0-9]+)', url)
    if not playlist_id_match:
        print("Invalid Spotify URL")
        return

    playlist_id = playlist_id_match.group(1)
    embed_url = f"https://open.spotify.com/embed/playlist/{playlist_id}"
    
    print(f"Fetching {embed_url}...")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        response = requests.get(embed_url, headers=headers)
        html = response.text
        
        # Extract JSON
        match = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.+?)</script>', html, re.DOTALL)
        if match:
            json_str = match.group(1)
            data = json.loads(json_str)
            
            # Print keys to help navigate
            print("\n--- JSON ROOT KEYS ---")
            print(list(data.keys()))
            
            print("\n--- PROPS KEYS ---")
            if 'props' in data:
                print(list(data['props'].keys()))
                if 'pageProps' in data['props']:
                    print("\n--- PAGEPROPS KEYS ---")
                    print(list(data['props']['pageProps'].keys()))
                    
                    # Try to dump the whole state structure to a file for analysis
                    with open("spotify_debug.json", "w", encoding="utf-8") as f:
                        json.dump(data, f, indent=2)
                    print("\nFull JSON saved to spotify_debug.json")
                    
                    # Try to find tracks
                    try:
                        # Check where tracks might be hidden
                        print("\nLooking for 'tracks' in common paths...")
                        
                        # Path 1: props.pageProps.state.data.entity.tracks
                        try:
                            state = data['props']['pageProps']['state']
                            print("Found 'state'")
                            entity = state['data']['entity']
                            print("Found 'data.entity'")
                            # Search for pagination info anywhere in the JSON
                            print("\nSearching for pagination keywords in full JSON...")
                            json_str_full = json.dumps(data)
                            for keyword in ['next', 'token', 'cursor', 'paging', 'offset', 'limit']:
                                matches = [m.start() for m in re.finditer(keyword, json_str_full, re.IGNORECASE)]
                                if matches:
                                    print(f"Found keyword '{keyword}' at indices: {matches[:5]}...")
                                    # Print context for first match
                                    start = max(0, matches[0] - 50)
                                    end = min(len(json_str_full), matches[0] + 100)
                                    print(f"Context: ...{json_str_full[start:end]}...")
                        except KeyError as e:
                            print(f"Path 1 failed at: {e}")
                            
                    except Exception as e:
                        print(f"Error exploring JSON: {e}")
            else:
                print("'props' not found in JSON data")
        else:
            print("No __NEXT_DATA__ script found in HTML")
            with open("spotify_debug.html", "w", encoding="utf-8") as f:
                f.write(html)
            print("HTML saved to spotify_debug.html")

    except Exception as e:
        print(f"Error: {e}")

# Test with a large playlist (RapCaviar, usually 50+ tracks)
debug_spotify_url("https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd")
