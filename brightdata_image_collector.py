#!/usr/bin/env python3
"""
Custom BrightData image collector that works with raw HTML format
Specifically designed for the user's serp_api1 zone configuration
"""
import requests
import json
import re
import os
import time
from pathlib import Path
from urllib.parse import urlparse, urljoin, quote_plus
import base64
from typing import List, Optional
from PIL import Image
import io

class BrightDataImageCollector:
    def __init__(self, api_key: str, zone: str = "serp_api1"):
        self.api_key = api_key
        self.zone = zone
        self.session = requests.Session()
        
    def collect_google_images(self, query: str, limit: int = 20) -> List[str]:
        """Collect image URLs from Google Images using BrightData raw HTML"""
        
        print(f"🔍 Searching Google Images for: {query}")
        
        # Use Google Images URL format with proper encoding
        encoded_query = quote_plus(query)
        search_url = f"https://www.google.com/search?q={encoded_query}&tbm=isch"
        
        payload = {
            "zone": self.zone,
            "url": search_url,
            "format": "raw"
        }
        
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        try:
            response = requests.post("https://api.brightdata.com/request", 
                                   headers=headers, json=payload, timeout=120)
            
            if response.status_code != 200:
                print(f"❌ BrightData request failed: {response.status_code}")
                print(f"Response: {response.text[:500]}")
                return []
                
            html_content = response.text
            print(f"✅ Got HTML response ({len(html_content)} chars)")
            
            # Extract image URLs from Google Images HTML
            image_urls = self.extract_image_urls_from_html(html_content)
            
            print(f"📸 Found {len(image_urls)} image URLs")
            return image_urls[:limit]
            
        except Exception as e:
            print(f"❌ Error collecting images: {e}")
            return []
    
    def extract_image_urls_from_html(self, html: str) -> List[str]:
        """Extract image URLs from Google Images HTML"""
        
        image_urls = []
        
        print(f"🔍 Analyzing HTML content...")
        
        # Modern Google Images embeds data in JavaScript objects
        # Look for AF_initDataCallback with image data
        js_data_pattern = r'AF_initDataCallback\([^}]+\}\);'
        js_matches = re.findall(js_data_pattern, html, re.DOTALL)
        
        print(f"📊 Found {len(js_matches)} JavaScript data blocks")
        
        # Enhanced patterns for different Google Images structures
        patterns = [
            # Original image URLs in JSON data (most reliable)
            r'"ou":"([^"]+)"',
            r'"ou":"([^"]*\.(?:jpg|jpeg|png|gif|webp)[^"]*)"',
            
            # Thumbnail URLs 
            r'"tu":"([^"]+)"',
            
            # Direct image URLs in various formats
            r'"([^"]*\.(?:jpg|jpeg|png|gif|webp)(?:\?[^"]*)?)"',
            
            # Image URLs in href attributes
            r'href="[^"]*imgurl=([^&"]+)',
            
            # Base64 encoded URLs
            r'data-src="([^"]+\.(?:jpg|jpeg|png|gif|webp)[^"]*)"',
            
            # Standard img src (fallback)
            r'<img[^>]+src="([^"]+\.(?:jpg|jpeg|png|gif|webp)[^"]*)"',
            
            # Alternative JSON structures
            r'\["([^"]*\.(?:jpg|jpeg|png|gif|webp)[^"]*)",\d+,\d+\]',
        ]
        
        # First, focus on JavaScript data blocks which contain the real image data
        for js_block in js_matches:
            for pattern in patterns:
                matches = re.findall(pattern, js_block, re.IGNORECASE)
                for match in matches:
                    # Handle tuple matches (some patterns return tuples)
                    url = match if isinstance(match, str) else match[0] if match else ""
                    
                    # Clean and validate URL
                    url = self.clean_url(url)
                    if self.is_valid_image_url(url):
                        image_urls.append(url)
        
        # If no URLs found in JS data, search the entire HTML
        if not image_urls:
            print("🔄 No URLs in JS blocks, searching entire HTML...")
            for pattern in patterns:
                matches = re.findall(pattern, html, re.IGNORECASE)
                for match in matches:
                    url = match if isinstance(match, str) else match[0] if match else ""
                    url = self.clean_url(url)
                    if self.is_valid_image_url(url):
                        image_urls.append(url)
        
        # Remove duplicates while preserving order
        seen = set()
        unique_urls = []
        for url in image_urls:
            if url not in seen:
                seen.add(url)
                unique_urls.append(url)
        
        print(f"🔗 Extracted {len(unique_urls)} unique URLs")
        
        # Debug: show first few URLs found
        if unique_urls:
            print("📸 Sample URLs found:")
            for i, url in enumerate(unique_urls[:3]):
                print(f"   {i+1}. {url[:80]}...")
        
        return unique_urls
    
    def clean_url(self, url: str) -> str:
        """Clean and decode URL"""
        if not url:
            return ""
            
        # Handle common encodings
        url = url.replace('\\u003d', '=')
        url = url.replace('\\u0026', '&') 
        url = url.replace('\\/', '/')
        url = url.replace('\\u003c', '<')
        url = url.replace('\\u003e', '>')
        
        # Handle URL encoding
        try:
            from urllib.parse import unquote
            url = unquote(url)
        except:
            pass
            
        return url.strip()
    
    def is_valid_image_url(self, url: str) -> bool:
        """Check if URL looks like a valid image URL"""
        if not url or len(url) < 10:
            return False
            
        # Must be HTTP/HTTPS
        if not url.startswith(('http://', 'https://')):
            return False
            
        # Skip obvious non-images
        skip_patterns = [
            'favicon.ico', 'logo', 'icon', 'avatar', 'profile',
            'button', 'arrow', 'star', 'badge', 'emoji',
            'google.com/images/branding', 'gstatic.com',
            'data:image/svg', 'base64'
        ]
        
        url_lower = url.lower()
        if any(pattern in url_lower for pattern in skip_patterns):
            return False
            
        # Should contain image file extension or be from image-likely domains
        image_extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
        image_domains = ['imgur.com', 'flickr.com', 'wikimedia.org']
        
        has_extension = any(ext in url_lower for ext in image_extensions)
        has_image_domain = any(domain in url_lower for domain in image_domains)
        
        # Accept if has image extension OR from image domain OR looks like an image URL
        return has_extension or has_image_domain or ('image' in url_lower and len(url) > 30)
    
    def download_image(self, url: str) -> Optional[bytes]:
        """Download an image from URL"""
        try:
            headers = {
                'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
            }
            response = self.session.get(url, headers=headers, timeout=30)
            
            if response.status_code == 200:
                content_type = response.headers.get('Content-Type', '').lower()
                if 'image' in content_type:
                    return response.content
                    
        except Exception as e:
            print(f"⚠️  Failed to download {url}: {e}")
        
        return None
    
    def save_images(self, image_urls: List[str], output_dir: Path, 
                   class_name: str = "etbr_gel") -> List[Path]:
        """Download and save images to disk"""
        
        output_dir = Path(output_dir)
        class_dir = output_dir / class_name
        class_dir.mkdir(parents=True, exist_ok=True)
        
        saved_files = []
        
        print(f"💾 Downloading {len(image_urls)} images to {class_dir}")
        
        for i, url in enumerate(image_urls):
            print(f"📥 [{i+1}/{len(image_urls)}] {url[:80]}...")
            
            # Download image
            image_data = self.download_image(url)
            if not image_data:
                continue
                
            # Try to open and validate image
            try:
                img = Image.open(io.BytesIO(image_data))
                img.verify()  # Verify it's a valid image
                
                # Re-open for actual processing (verify() closes the image)
                img = Image.open(io.BytesIO(image_data))
                
                # Basic quality filters
                width, height = img.size
                if width < 100 or height < 100:
                    print(f"   ⚠️  Too small ({width}x{height})")
                    continue
                    
                if width > 2000 or height > 2000:
                    print(f"   📏 Resizing large image ({width}x{height})")
                    img.thumbnail((2000, 2000), Image.Resampling.LANCZOS)
                
                # Save image
                filename = f"image_{i+1:03d}.jpg"
                save_path = class_dir / filename
                
                # Convert to RGB if needed
                if img.mode != 'RGB':
                    img = img.convert('RGB')
                    
                img.save(save_path, 'JPEG', quality=85)
                saved_files.append(save_path)
                
                print(f"   ✅ Saved as {filename} ({img.size[0]}x{img.size[1]})")
                
            except Exception as e:
                print(f"   ❌ Invalid image: {e}")
                continue
            
            # Add small delay to be respectful
            time.sleep(0.5)
        
        print(f"🎉 Successfully saved {len(saved_files)} images")
        return saved_files

def main():
    # Configuration
    api_key = "3584aadc6a9506af30ad9b12067aea166f2df11141ce6e052f7814a42793d185"
    zone = "serp_api1"
    query = "EtBr agarose gel electrophoresis DNA bands fluorescent"
    output_dir = Path("test_brightdata_images")
    limit = 15
    
    collector = BrightDataImageCollector(api_key, zone)
    
    print("=" * 60)
    print("BrightData Image Collection Test")
    print("=" * 60)
    
    # Collect image URLs
    image_urls = collector.collect_google_images(query, limit)
    
    if not image_urls:
        print("❌ No image URLs found")
        return
    
    # Download and save images
    saved_files = collector.save_images(image_urls, output_dir, "etbr_gel")
    
    print(f"\n🎯 Collection complete!")
    print(f"📁 Images saved to: {output_dir}")
    print(f"📊 Total images: {len(saved_files)}")

if __name__ == "__main__":
    main()