import urllib.request
import json
import ssl

api_key = "AIzaSyDBEPLz2a6NURADqeMzRMzOEpRetGWbHG8"
model = "gemini-pro"
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
