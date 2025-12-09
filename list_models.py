import urllib.request
import json
import ssl

def get_api_key():
    try:
        with open('local.properties', 'r') as f:
            for line in f:
                if 'GEMINI_API_KEY' in line:
                    key = line.split('=')[1].strip()
                    return key.replace('"', '').replace("'", "")
    except Exception as e:
        print(f"Error reading local.properties: {e}")
        return None

api_key = get_api_key()
print(f"Using Key: {api_key[:10]}...") # Print first 10 chars for verification

if not api_key:
    print("No API Key found!")
    exit(1)

url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"

try:
    context = ssl._create_unverified_context()
    
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, context=context) as response:
        print(f"Status Code: {response.getcode()}")
        data = json.loads(response.read().decode('utf-8'))
        print(json.dumps(data, indent=2))

except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"Error Content: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error: {e}")
