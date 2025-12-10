import os
import json
import urllib.request
import urllib.error

def get_api_key():
    local_props_path = os.path.join(os.path.dirname(__file__), 'local.properties')
    if os.path.exists(local_props_path):
        try:
            with open(local_props_path, 'r') as f:
                for line in f:
                    if line.strip().startswith('GEMINI_API_KEY'):
                        parts = line.split('=')
                        if len(parts) >= 2:
                            val = parts[1].strip()
                            return val.strip('"').strip("'")
        except Exception as e:
            print(f"Error reading local.properties: {e}")
    return None

def test_model(api_key, model_name):
    print(f"\n--- Testing Model: {model_name} ---")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
    headers = {'Content-Type': 'application/json'}
    data = {"contents": [{"parts": [{"text": "Hello"}]}]}
    
    try:
        req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method='POST')
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            print(f"SUCCESS with {model_name}!")
            return True
    except urllib.error.HTTPError as e:
        print(f"FAILED with {model_name}")
        print(f"Status: {e.code}")
        try:
            print(e.read().decode('utf-8'))
        except:
            print("(No error body)")
        return False
    except Exception as e:
        print(f"ERROR with {model_name}: {e}")
        return False

if __name__ == "__main__":
    print("--- Gemini API Debugger v4 ---")
    key = get_api_key()
    
    if not key:
        print("Could not find GEMINI_API_KEY in local.properties")
    else:
        print(f"Found Key: {key[:5]}...{key[-5:]}")
        
        models = ["gemini-1.5-flash", "gemini-pro"]
        for model in models:
            test_model(key, model)
            print("-" * 20)
