#!/usr/bin/env python3
"""
Test different BrightData zone configurations and API endpoints
"""
import requests
import json

def test_without_zone(api_key: str):
    """Test BrightData API without specifying a zone"""
    
    print("Testing without zone specification...")
    
    payload = {
        "search_engine": "bing",
        "query": "test",
        "data_format": "parsed_bing_api",
        "format": "json"
        # No zone specified
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    try:
        response = requests.post("https://api.brightdata.com/request", 
                               headers=headers, json=payload, timeout=60)
        
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.text}")
        
        return response.status_code == 200
        
    except Exception as e:
        print(f"Error: {e}")
        return False

def test_account_info(api_key: str):
    """Try to get account information to see available zones"""
    
    print("Testing account info endpoint...")
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    # Try common BrightData endpoints
    endpoints = [
        "https://api.brightdata.com/api/account",
        "https://api.brightdata.com/zones",
        "https://api.brightdata.com/account/zones",
        "https://api.brightdata.com/v1/account",
        "https://api.brightdata.com/v1/zones"
    ]
    
    for endpoint in endpoints:
        try:
            print(f"Trying: {endpoint}")
            response = requests.get(endpoint, headers=headers, timeout=30)
            print(f"  Status: {response.status_code}")
            if response.status_code == 200:
                data = response.json()
                print(f"  Success! Data keys: {list(data.keys())}")
                return data
            else:
                print(f"  Response: {response.text[:200]}")
        except Exception as e:
            print(f"  Error: {e}")
    
    return None

if __name__ == "__main__":
    api_key = "3584aadc6a9506af30ad9b12067aea166f2df11141ce6e052f7814a42793d185"
    
    print("=" * 60)
    print("BrightData Zone Investigation")
    print("=" * 60)
    
    # Test without zone
    test_without_zone(api_key)
    
    print("\n" + "=" * 60)
    
    # Try to get account info
    account_data = test_account_info(api_key)
    
    if account_data:
        print(f"Account data: {json.dumps(account_data, indent=2)}")