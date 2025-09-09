#!/usr/bin/env python3
"""
Quick test script to verify BrightData API credentials and connectivity
"""
import requests
import json

def test_brightdata_api(api_key: str, zone: str = "datacenter"):
    """Test BrightData API with a simple search"""
    
    print(f"Testing BrightData API...")
    print(f"API Key: {api_key[:10]}...{api_key[-10:]}")  # Show partial key for verification
    print(f"Zone: {zone}")
    
    # Simple test payload - search for "test" in Bing
    payload = {
        "search_engine": "bing",
        "query": "test",
        "data_format": "parsed_bing_api",
        "format": "json",
        "zone": zone
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    endpoint = "https://api.brightdata.com/request"
    
    try:
        print(f"Sending request to: {endpoint}")
        response = requests.post(endpoint, headers=headers, json=payload, timeout=60)
        
        print(f"Status Code: {response.status_code}")
        print(f"Headers: {dict(response.headers)}")
        
        if response.status_code == 200:
            data = response.json()
            print("✅ SUCCESS - API request successful!")
            print(f"Response keys: {list(data.keys())}")
            
            # Check if we got any results
            organic = data.get("response", {}).get("organic", [])
            if organic:
                print(f"✅ Found {len(organic)} organic results")
                print(f"First result: {organic[0].get('title', 'No title')}")
            else:
                print("⚠️  No organic results found")
                
            return True
            
        else:
            print(f"❌ FAILED - HTTP {response.status_code}")
            print(f"Response: {response.text}")
            return False
            
    except requests.exceptions.Timeout:
        print("❌ FAILED - Request timed out")
        return False
    except requests.exceptions.RequestException as e:
        print(f"❌ FAILED - Request error: {e}")
        return False
    except json.JSONDecodeError as e:
        print(f"❌ FAILED - JSON decode error: {e}")
        print(f"Response text: {response.text}")
        return False
    except Exception as e:
        print(f"❌ FAILED - Unexpected error: {e}")
        return False

if __name__ == "__main__":
    # Use your BrightData API key
    api_key = "3584aadc6a9506af30ad9b12067aea166f2df11141ce6e052f7814a42793d185"
    
    # Test with datacenter zone first
    print("=" * 50)
    print("Testing with 'datacenter' zone...")
    success = test_brightdata_api(api_key, "datacenter")
    
    if not success:
        print("\n" + "=" * 50)
        print("Testing with 'static' zone...")
        test_brightdata_api(api_key, "static")
        
        print("\n" + "=" * 50)
        print("Testing with 'residential' zone...")
        test_brightdata_api(api_key, "residential")