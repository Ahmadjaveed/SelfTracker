import json

try:
    with open('models.json', 'r', encoding='utf-16') as f:
        # Skip the first line "Status Code: 200" if present
        content = f.read()
        if content.startswith("Status Code:"):
            content = content.split('\n', 1)[1]
        
        data = json.loads(content)
        print("Available Models:")
        with open('model_names.txt', 'w', encoding='utf-8') as outfile:
            for model in data.get('models', []):
                if "gemini" in model['name'].lower():
                    outfile.write(f"{model['name']}\n")
                    # print(f"- {model['name']}")

except Exception as e:
    print(f"Error parsing JSON: {e}")
    # Fallback: try utf-8
    try:
        with open('models.json', 'r', encoding='utf-8') as f:
             content = f.read()
             if content.startswith("Status Code:"):
                content = content.split('\n', 1)[1]
             data = json.loads(content)
             print("Available Models (UTF-8):")
             for model in data.get('models', []):
                print(f"- {model['name']}")
    except Exception as e2:
        print(f"Error parsing JSON with UTF-8: {e2}")
