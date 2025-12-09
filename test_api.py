import urllib.request
import json
import ssl

def get_api_key():
    try:
        with open('local.properties', 'r') as f:
            for line in f:
                if 'GEMINI_API_KEY' in line:
                    key = line.split('=')[1].strip()
                    # Remove potential quotes
                    return key.replace('"', '').replace("'", "")
    except Exception as e:
        print(f"Error reading local.properties: {e}")
        return None

api_key = get_api_key()
# Try a stable model that is likely to exist for everyone
model = "gemini-1.5-flash"
url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

headers = {
    "Content-Type": "application/json"
}

data = {
    "contents": [{
        "parts": [{"text": "Hello"}]
    }]
}

try:
    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method='POST')
    # Create unverified context to avoid potential SSL cert issues in dev envs
    context = ssl._create_unverified_context()
    
    with urllib.request.urlopen(req, context=context) as response:
        print(f"Status Code: {response.getcode()}")
        print(f"Response: {response.read().decode('utf-8')}")

except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"Error Content: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error: {e}")
