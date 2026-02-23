import urllib.request
import ssl

# Ignore SSL errors just in case
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

url = "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M"
headers = {'User-Agent': "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}

req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req, context=ctx) as response:
        data = response.read()
        with open("spotify_real.html", "wb") as f:
            f.write(data)
    print(f"Downloaded spotify_real.html, size: {len(data)}")
except Exception as e:
    print(f"Error: {e}")
