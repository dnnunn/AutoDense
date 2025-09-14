#!/usr/bin/env python3
"""
Targeted EtBr Image Collection
Collects high-quality EtBr agarose gel images using multiple strategies
"""
import json
import time
import logging
from pathlib import Path
from typing import List, Dict
import shutil
from brightdata_image_collector import BrightDataImageCollector

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class EtBrImageCollector:
    def __init__(self):
        self.api_key = "3584aadc6a9506af30ad9b12067aea166f2df11141ce6e052f7814a42793d185"
        self.zone = "serp_api1"
        self.collector = BrightDataImageCollector(self.api_key, self.zone)
        
    def collect_targeted_etbr_images(self, output_dir: Path = Path("user_seed_images/etbr_agarose"), 
                                   target_count: int = 20) -> List[Path]:
        """Collect high-quality EtBr images using targeted search queries"""
        
        logger.info(f"🎯 Collecting {target_count} high-quality EtBr images")
        
        output_dir.mkdir(parents=True, exist_ok=True)
        
        # Highly specific EtBr search queries
        etbr_queries = [
            # Academic/research focused
            "EtBr agarose gel electrophoresis DNA molecular biology",
            "ethidium bromide stained agarose gel UV transilluminator",
            "DNA gel electrophoresis EtBr fluorescent bands laboratory",
            "molecular cloning agarose gel EtBr DNA fragments",
            
            # Educational/protocol focused  
            "agarose gel electrophoresis protocol EtBr staining DNA",
            "DNA gel electrophoresis lab technique ethidium bromide",
            "molecular biology gel electrophoresis EtBr visualization",
            
            # Specific applications
            "PCR products agarose gel EtBr analysis DNA",
            "restriction enzyme digestion agarose gel EtBr",
            "DNA ladder molecular weight marker EtBr gel",
            
            # Equipment/setup focused
            "UV transilluminator EtBr stained DNA gel",
            "gel documentation system EtBr DNA bands",
            "agarose gel casting EtBr DNA electrophoresis"
        ]
        
        collected_files = []
        existing_count = len(list(output_dir.glob("*.jpg")))
        
        logger.info(f"📊 Starting collection (already have {existing_count} images)")
        
        for i, query in enumerate(etbr_queries):
            if len(collected_files) >= target_count:
                break
                
            logger.info(f"🔍 [{i+1}/{len(etbr_queries)}] Searching: {query}")
            
            try:
                # Collect images for this query
                images_per_query = min(8, target_count - len(collected_files))
                image_urls = self.collector.collect_google_images(query, images_per_query)
                
                if not image_urls:
                    logger.warning(f"⚠️  No URLs found for: {query}")
                    continue
                
                # Download and save images
                temp_dir = Path("temp_etbr_collection")
                saved_files = self.collector.save_images(image_urls, temp_dir, "etbr_temp")
                
                if saved_files:
                    # Move good images to final location with descriptive names
                    for j, temp_file in enumerate(saved_files):
                        # Create descriptive filename
                        query_short = query.split()[0:3]  # First 3 words
                        query_tag = "_".join(query_short).lower().replace(",", "")
                        
                        final_name = f"etbr_{len(collected_files)+1:03d}_{query_tag}_{j+1}.jpg"
                        final_path = output_dir / final_name
                        
                        shutil.move(temp_file, final_path)
                        collected_files.append(final_path)
                        
                        logger.info(f"   ✅ Saved: {final_name}")
                
                # Clean up temp directory
                if temp_dir.exists():
                    shutil.rmtree(temp_dir)
                
                # Add delay between queries to be respectful
                time.sleep(2)
                
            except Exception as e:
                logger.error(f"❌ Error with query '{query}': {e}")
                continue
        
        total_images = len(list(output_dir.glob("*.jpg")))
        new_images = len(collected_files)
        
        logger.info(f"🎉 Collection complete!")
        logger.info(f"📊 New images collected: {new_images}")
        logger.info(f"📊 Total EtBr images now: {total_images}")
        
        return collected_files
    
    def quality_filter_images(self, images_dir: Path) -> Dict:
        """Apply basic quality filtering to collected images"""
        
        logger.info(f"🔍 Applying quality filters to images in {images_dir}")
        
        from PIL import Image
        import os
        
        quality_report = {
            "total_images": 0,
            "kept_images": 0,
            "removed_images": 0,
            "removal_reasons": {},
            "kept_files": []
        }
        
        for img_file in images_dir.glob("*.jpg"):
            quality_report["total_images"] += 1
            
            try:
                with Image.open(img_file) as img:
                    width, height = img.size
                    file_size = os.path.getsize(img_file)
                    
                    # Quality criteria
                    remove_reasons = []
                    
                    # Size criteria
                    if width < 200 or height < 150:
                        remove_reasons.append("too_small")
                    
                    # Aspect ratio (gels are usually wider than tall)
                    aspect_ratio = width / height
                    if aspect_ratio < 0.8 or aspect_ratio > 4.0:
                        remove_reasons.append("bad_aspect_ratio")
                    
                    # File size (very small files often low quality)
                    if file_size < 5000:  # 5KB
                        remove_reasons.append("tiny_file")
                    
                    # Very large files might be diagrams/posters
                    if file_size > 5_000_000:  # 5MB
                        remove_reasons.append("huge_file")
                    
                    # Decide whether to keep
                    if remove_reasons:
                        # Remove image
                        logger.info(f"   ❌ Removing {img_file.name}: {', '.join(remove_reasons)}")
                        img_file.unlink()
                        quality_report["removed_images"] += 1
                        
                        for reason in remove_reasons:
                            quality_report["removal_reasons"][reason] = quality_report["removal_reasons"].get(reason, 0) + 1
                    else:
                        # Keep image
                        quality_report["kept_images"] += 1
                        quality_report["kept_files"].append(str(img_file))
                        logger.info(f"   ✅ Kept {img_file.name} ({width}x{height}, {file_size//1000}KB)")
                        
            except Exception as e:
                logger.error(f"❌ Error processing {img_file}: {e}")
                quality_report["removed_images"] += 1
        
        logger.info(f"📊 Quality filtering complete:")
        logger.info(f"   • Total processed: {quality_report['total_images']}")
        logger.info(f"   • Kept: {quality_report['kept_images']}")
        logger.info(f"   • Removed: {quality_report['removed_images']}")
        
        return quality_report
    
    def create_collection_summary(self, images_dir: Path, quality_report: Dict) -> Path:
        """Create a summary of the collected images"""
        
        summary = {
            "collection_date": time.strftime("%Y-%m-%d %H:%M:%S"),
            "images_directory": str(images_dir),
            "quality_filter_results": quality_report,
            "images_for_annotation": quality_report["kept_files"],
            "next_steps": [
                "Review the collected images in user_seed_images/etbr_agarose/",
                "Select 3-10 of the best images as your seed images",
                "Create annotations for each seed image",
                "Run the curated ML optimization"
            ]
        }
        
        summary_file = images_dir.parent / "etbr_collection_summary.json"
        with open(summary_file, 'w') as f:
            json.dump(summary, f, indent=2)
        
        logger.info(f"📄 Collection summary saved: {summary_file}")
        return summary_file

def main():
    print("=" * 60)
    print("🧬 EtBr Agarose Gel Image Collection")
    print("=" * 60)
    
    collector = EtBrImageCollector()
    
    try:
        # Collect targeted EtBr images
        print("\n🎯 Phase 1: Collecting targeted EtBr images...")
        collected_files = collector.collect_targeted_etbr_images(target_count=25)
        
        if not collected_files:
            print("❌ No images collected. Check your internet connection and BrightData setup.")
            return 1
        
        # Apply quality filtering
        print("\n🔍 Phase 2: Applying quality filters...")
        images_dir = Path("user_seed_images/etbr_agarose")
        quality_report = collector.quality_filter_images(images_dir)
        
        # Create summary
        print("\n📊 Phase 3: Creating collection summary...")
        summary_file = collector.create_collection_summary(images_dir, quality_report)
        
        # Final report
        print(f"\n" + "=" * 60)
        print("✅ EtBr Image Collection Complete!")
        print("=" * 60)
        
        print(f"📁 Images saved to: {images_dir}")
        print(f"📊 High-quality images: {quality_report['kept_images']}")
        print(f"📄 Summary report: {summary_file}")
        
        print(f"\n🎯 Next Steps:")
        print(f"1. 👁️  Review images in: {images_dir}")
        print(f"2. 🎯 Select 3-10 best images as seed images")
        print(f"3. 📝 Create annotations for each seed image")
        print(f"4. 🚀 Run: python curated_image_ml_optimizer.py")
        
        return 0
        
    except Exception as e:
        print(f"\n❌ Collection failed: {e}")
        return 1

if __name__ == "__main__":
    exit(main())