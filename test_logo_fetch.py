import requests

def check(domain):
    print(f"Checking {domain}...")
    
    # 1. Clearbit High Res
    cb_url = f"https://logo.clearbit.com/{domain}?size=512"
    try:
        r = requests.head(cb_url, timeout=2)
        print(f"  Clearbit ({cb_url}): {r.status_code}")
    except Exception as e:
        print(f"  Clearbit Error: {e}")

    # 2. Google High Res
    g_url = f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://{domain}&size=512"
    try:
        r = requests.get(g_url, timeout=2)
        print(f"  Google ({g_url}): {r.status_code} - Content-Length: {r.headers.get('content-length')}")
    except Exception as e:
        print(f"  Google Error: {e}")

check("python.org")
check("google.com")
