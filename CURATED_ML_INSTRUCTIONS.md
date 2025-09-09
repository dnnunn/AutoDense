
# 🎯 Curated ML Optimization Setup Instructions

## 📁 Folder Structure

### 1. `user_seed_images/` - Your Curated Images
Place your high-quality seed images here:

**For EtBr Agarose Gels:**
- `user_seed_images/etbr_agarose/`
- Add 3-10 of your best EtBr gel images
- These should be representative of what you want the system to find
- Good lighting, clear bands, typical lane counts

**For SDS-PAGE Gels:**
- `user_seed_images/sds_page/`  
- Add 3-10 of your best SDS-PAGE images
- Clear protein bands, good separation

**For Colony Counting:**
- `user_seed_images/colony_count/`
- Add 3-10 representative colony plate images

### 2. `user_annotations/` - Your Expert Annotations
Edit the template files with your real data:

**File: `etbr_agarose_annotations.json`**
- Copy from `etbr_agarose_template.json`
- Update each entry with your actual image analysis:
  - `image_path`: Path to your seed image
  - `lanes_total`: Number of lanes you count
  - `bands_total`: Number of bands you count  
  - `confidence`: How confident you are (0.0-1.0)
  - `notes`: Any observations

## 🚀 Usage Steps

### Step 1: Add Your Images
```bash
# Copy your best images to:
user_seed_images/etbr_agarose/my_gel_1.jpg
user_seed_images/etbr_agarose/my_gel_2.jpg  
user_seed_images/etbr_agarose/my_gel_3.jpg
# ... etc
```

### Step 2: Create Your Annotations
```bash
# Copy template and edit:
cp user_annotations/etbr_agarose_template.json user_annotations/etbr_agarose_annotations.json

# Edit user_annotations/etbr_agarose_annotations.json with your real data
```

### Step 3: Run ML Optimization
```bash
python curated_image_ml_optimizer.py
```

## 📊 Annotation Format Example

```json
{
  "image_path": "user_seed_images/etbr_agarose/my_gel_1.jpg",
  "lanes_total": 18,
  "bands_total": 22, 
  "confidence": 0.95,
  "notes": "Clear gel, all lanes loaded well, ladder in lane 1",
  "task_type": "etbr_agarose"
}
```

## 💡 Tips for Good Seed Images

1. **Representative**: Choose images typical of your lab's conditions
2. **High Quality**: Good lighting, clear bands, in-focus
3. **Variety**: Different lane counts, band patterns, intensities
4. **Realistic**: Include some challenging images, not just perfect ones
5. **Consistent**: Similar imaging conditions (UV, camera settings)

## 🎯 What the System Will Do

1. **Learn** from your seed images (what good images look like)
2. **Search** for similar images using ImageHarvester + BrightData
3. **Filter** candidates using ML similarity to your seeds
4. **Optimize** parameters using your expert annotations as ground truth
5. **Generate** optimized AutoDense configuration file

## 📁 Results Location

Results will be saved in:
- `curated_ml_results/` - All optimization results
- `curated_ml_results/etbr_agarose_optimized_config.yaml` - Optimized config
- `curated_ml_results/etbr_agarose_curated_optimization_summary.json` - Summary

Ready to start! 🚀
