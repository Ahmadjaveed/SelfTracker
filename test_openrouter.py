import requests
import json

API_KEY = "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"
API_URL = "https://openrouter.ai/api/v1/chat/completions"

models = [
    "google/gemini-2.0-flash-exp:free",
    "google/gemini-2.0-flash-thinking-exp:free",
    "meta-llama/llama-3.1-8b-instruct:free",
    "mistralai/mistral-7b-instruct:free",
    "microsoft/phi-3-medium-128k-instruct:free",
    "qwen/qwen-2-7b-instruct:free",
    "google/gemini-2.0-pro-exp-02-05:free"
]

def test_model(model_name):
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": "http://localhost",
        "X-Title": "SelfTracker Test"
    }

    data = {
        "model": model_name,
        "messages": [{"role": "user", "content": "Say hello"}],
    }

    print(f"Testing {model_name}...")
    try:
        response = requests.post(API_URL, headers=headers, data=json.dumps(data), timeout=10)
        if response.status_code == 200:
            print(f"✅ Success: {model_name}")
            return True
        else:
            print(f"❌ Failed: {model_name} - {response.status_code} - {response.text}")
            return False
    except Exception as e:
        print(f"⚠️ Error: {model_name} - {str(e)}")
        return False

print("Starting OpenRouter Model Verification...")
success_count = 0
for model in models:
    if test_model(model):
        success_count += 1
    print("-" * 30)

print(f"Verification Complete. {success_count}/{len(models)} models operational.")
