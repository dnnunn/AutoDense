# AutoDense AI Integration Prompts

> **Doc Meta**
> - **Purpose:** System prompts for OpenAI ChatGPT-4.1 integration with AutoDense Band Assist Pipeline
> - **Scope:** Current AI preprocessing and analysis guidance prompts (September 2025)
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-12

## Overview

AutoDense uses **OpenAI ChatGPT-4.1** for intelligent image preprocessing and analysis guidance in the **Band Assist Pipeline**. This directory contains the system prompts that define AI behavior.

## Current Architecture (2025)

**Primary Workflow:**
1. **Image Upload** → User uploads gel electrophoresis image
2. **AI Preprocessing** → OpenAI ChatGPT-4.1 analyzes and enhances image
3. **Band Analysis** → Python-based scientific analysis pipeline
4. **Results Display** → Streamlit UI presents analysis results

**Key Components:**
- `preprocessing.system.md` - OpenAI prompt for image preprocessing and enhancement
- `analysis.system.md` - OpenAI prompt for analysis guidance and interpretation
- `README.md` - This documentation file

## Integration Points

**Streamlit UI Integration:**
- `openai_guided_preprocess()` function calls OpenAI with preprocessing prompt
- `guarded_preprocess()` function provides fallback analysis
- Results feed into Python scientific computing stack (numpy, scipy, scikit-image)

**Configuration:**
- API settings in `configs/api-config.properties`
- OpenAI model: ChatGPT-4.1
- Timeout handling with concurrent.futures
- Caching and performance optimization

## Migration Notes

**Previous System (Legacy):**
- Complex Gemini-based autotune optimization
- Multi-tool parameter adjustment workflow  
- PatchProposal and RunReport systems
- Challenge pack configurations

**Current System (Active):**
- Direct OpenAI image analysis and preprocessing
- Streamlined Band Assist Pipeline
- Real-time feedback and enhancement
- Simplified user workflow
