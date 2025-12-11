import requests
import urllib.parse
import json

def check_logo_logic(query):
    print(f"\n--- Testing Logic for: '{query}' ---")
    
    # 1. Clean Query
    clean_query = query.strip()
    
    # helper to fetch from Clearbit Autocomplete
    def fetch_autocomplete(q):
        url = f"https://autocomplete.clearbit.com/v1/companies/suggest?query={urllib.parse.quote(q)}"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }
        print(f"  [Step 1] Asking Clearbit: {url}")
        try:
            res = requests.get(url, headers=headers, timeout=5)
            if res.status_code == 200:
                data = res.json()
                if data and len(data) > 0:
                    first = data[0]
                    name = first.get("name")
                    domain = first.get("domain")
                    logo = first.get("logo")
                    print(f"    -> Found: Name='{name}', Domain='{domain}', Logo='{logo}'")
                    
                    # Priority 1: High-Res Clearbit (512px)
                    if domain:
                        high_res_url = f"https://logo.clearbit.com/{domain}?size=512"
                        print(f"    -> Checking High-Res Clearbit: {high_res_url}")
                        try:
                            hr_head = requests.head(high_res_url, timeout=2)
                            if hr_head.status_code == 200:
                                print("      [SUCCESS] High-Res Clearbit Valid!")
                                return high_res_url
                        except:
                            print("      [FAIL] High-Res check failed.")
                    
                    # Priority 2: Standard Autocomplete Logo (128px)
                    if logo:
                         print(f"    -> Returning Standard Logo: {logo}")
                         return logo

                    # Priority 3: Google Fallback (High Res 512px)
                    if domain:
                        fav_url = f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://{domain}&size=512"
                        print(f"    -> Converted Domain to Google Icon: {fav_url}")
                        return fav_url
                else:
                    print("    -> No matches found in JSON.")
            else:
                print(f"    -> API Error: {res.status_code}")
        except Exception as e:
            print(f"    -> Exception: {e}")
        return None

    # Logic Flow
    # Try 1: Exact
    result = fetch_autocomplete(clean_query)
    if result:
        print(f"  [SUCCESS] Found via Exact Query: {result}")
        return

    # Try 2: Clean verbs
    verbs = ["Learn", "Master", "Study", "Practice", "Become", "Get"]
    modified_query = clean_query
    changed = False
    for verb in verbs:
        if modified_query.lower().startswith(verb.lower() + " "):
            modified_query = modified_query[len(verb)+1:]
            changed = True
    
    if changed:
        print(f"  [Step 2] Cleaning Verb. New Query: '{modified_query}'")
        result = fetch_autocomplete(modified_query)
        if result:
            print(f"  [SUCCESS] Found via Cleaned Query: {result}")
            return

    # Try 3: Blind Guess
    guess_domain = modified_query.replace(" ", "").lower() + ".com"
    guess_url = f"https://logo.clearbit.com/{guess_domain}"
    print(f"  [Step 3] Blind Guess: {guess_url}")
    try:
        head = requests.head(guess_url, timeout=5)
        if head.status_code == 200:
             print(f"  [SUCCESS] Blind Guess Valid! Status: {head.status_code}")
             return
        else:
             print(f"    -> Failed. Status: {head.status_code}")
    except Exception as e:
        print(f"    -> Exception: {e}")

    print("  [FAILURE] Could not find logo.")

if __name__ == "__main__":
    check_logo_logic("Python")
    check_logo_logic("Learn Python")
    check_logo_logic("Nike")
    check_logo_logic("Skibidi Toilet") # Expected fail

