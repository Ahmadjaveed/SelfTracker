import requests
import urllib.parse
import json

def test_clearbit(query):
    encoded_query = urllib.parse.quote(query)
    url = f"https://autocomplete.clearbit.com/v1/companies/suggest?query={encoded_query}"
    print(f"Testing URL: {url}")
    
    try:
        response = requests.get(url)
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("Response JSON:")
            print(json.dumps(data, indent=2))
            
            if data and len(data) > 0:
                print(f"First Match Logo: {data[0].get('logo')}")
            else:
                print("No matches found.")
        else:
            print(f"Error: {response.text}")
            
    except Exception as e:
        print(f"Exception: {e}")

if __name__ == "__main__":
    test_clearbit("Learn Python")
    test_clearbit("Master Java")
    test_clearbit("Build a Website")
    test_clearbit("Nike")
