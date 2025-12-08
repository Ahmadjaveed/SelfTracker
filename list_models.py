import urllib.request
import json
import ssl

api_key = "AIzaSyDBEPLz2a6NURADqeMzRMzOEpRetGWbHG8"
url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"

try:
    # Create unverified context to avoid SSL issues
    context = ssl._create_unverified_context()
    
    with urllib.request.urlopen(url, context=context) as response:
        print(f"Status Code: {response.getcode()}")
        data = json.loads(response.read().decode('utf-8'))
        print(json.dumps(data, indent=2))

except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"Error Content: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error: {e}")
