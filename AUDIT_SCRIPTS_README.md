# AutoDense Systematic Audit Scripts

This directory contains scripts for systematically testing AutoDense with different parameter configurations across multiple test images.

## Overview

The audit system tests **36 combinations** of:
- **9 test images** (3 colony, 3 etbr, 3 sds)
- **4 parameter variations** (full, no_deskew, no_background, no_normalization)

Each test runs for 30 seconds and collects all outputs for analysis.

## Files

### Main Scripts (Bash 3.2 Compatible - Recommended)
- `audit_systematic_test_compat.sh` - Main audit script
- `audit_dry_run_compat.sh` - Validation and preview script

### Legacy Scripts (Require Bash 4.0+)
- `audit_systematic_test.sh` - Main audit script (associative arrays)
- `audit_dry_run.sh` - Validation script (associative arrays)

## Quick Start

1. **Validate setup:**
   ```bash
   ./audit_dry_run_compat.sh
   ```

2. **Run full audit:**
   ```bash
   ./audit_systematic_test_compat.sh
   ```

## Test Images

### Colony Analysis (3 images)
- `tmp/colony_plate.jpg`
- `tmp/synthetic_colony_plate.jpg`
- `tmp/synthetic_colony_plate2.png`

### EtBr Gel Analysis (3 images)
- `tmp/etbr_gel.jpg`
- `tmp/synthetic_etbr_gel.jpg`
- `tmp/synthetic_etbr_gel2.png`

### SDS-PAGE Analysis (3 images)
- `tmp/sds_gel.jpg`
- `tmp/synthetic_sds_gel.jpg`
- `tmp/synthetic_sds_gel2.png`

## Parameter Variations

1. **full** - Baseline configuration (no modifications)
2. **no_deskew** - Disable image deskewing (`enable_deskew: false`)
3. **no_background** - Disable background removal (`background_removal_radius: 0.0`)
4. **no_normalization** - Disable intensity normalization (`normalize_intensity: false`)

## Make Targets

The scripts automatically map images to the correct make targets:
- Colony images → `make tune-colony-ij`
- EtBr images → `make tune-etbr-ij`
- SDS images → `make tune-sds-ij`

## Configuration Files

- `configs/colony.yaml` - Colony analysis parameters
- `configs/etbr.yaml` - EtBr gel analysis parameters
- `configs/sds.yaml` - SDS-PAGE analysis parameters

## Output Structure

Results are saved to `systematic_test_results/YYYYMMDD_HHMMSS/`:

```
20250829_123456/
├── audit_summary.json          # Test run statistics
├── README.md                   # Results documentation
├── colony/                     # Colony analysis results
│   ├── colony_plate_full/
│   │   ├── config_original_backup.yaml
│   │   ├── config_used.yaml
│   │   ├── make_output.log
│   │   ├── test_metadata.json
│   │   ├── output/            # Analysis outputs
│   │   ├── stages/            # Stage images
│   │   └── reports/           # JSON reports
│   ├── colony_plate_no_deskew/
│   └── ...
├── etbr/                       # EtBr analysis results
└── sds/                        # SDS-PAGE analysis results
```

## Individual Test Results

Each test directory contains:
- `config_original_backup.yaml` - Original configuration before modification
- `config_used.yaml` - Configuration used for this specific test
- `make_output.log` - Complete output from the make command
- `test_metadata.json` - Test run metadata (timestamps, paths, etc.)
- `output/` - Analysis output files from ImageJ
- `stages/` - Intermediate stage*.png images
- `reports/` - JSON analysis reports

## Safety Features

### Configuration Safety
- Original configurations are always backed up before modification
- Configurations are automatically restored after each test
- Failed tests don't affect subsequent tests

### Process Safety
- Each test runs for exactly 30 seconds then terminates
- Background processes are cleaned up on script exit
- Robust error handling with detailed logging

### Data Safety
- All outputs are collected before moving to next test
- Timestamped results directories prevent overwriting
- Complete audit trail for all operations

## Runtime Estimates

- **Per test:** ~35 seconds (30s run + 5s setup/cleanup)
- **Total time:** ~21 minutes for all 36 tests
- **Disk space:** Varies by analysis outputs (typically 100-500MB per test)

## Prerequisites

- All test images must exist in `tmp/` directory
- Configuration files must exist in `configs/` directory
- Must be run from AutoDense root directory
- Make targets must be available and functional

## Troubleshooting

### Common Issues

1. **"Missing test images"**
   - Ensure all 9 test images exist in `tmp/` directory
   - Check filenames match exactly (case-sensitive)

2. **"Must be run from AutoDense root directory"**
   - Script must be run from directory containing `Makefile` and `configs/`

3. **Make command failures**
   - Check that `make tune-*-ij` targets work individually
   - Verify ImageJ and Python environment setup
   - Check GEMINI_API_KEY is set

4. **Bash compatibility issues**
   - Use `*_compat.sh` versions for macOS default bash (3.2)
   - Install bash 4.0+ for associative array versions

### Logs and Debugging

- Each test creates detailed logs in its output directory
- Main script provides colored progress output
- Use dry run script to validate setup before full run

## Example Usage

```bash
# Navigate to AutoDense directory
cd /path/to/AutoDense

# Validate setup
./audit_dry_run_compat.sh

# Run full audit if validation passes
./audit_systematic_test_compat.sh

# Results will be in:
# systematic_test_results/YYYYMMDD_HHMMSS/
```

## Integration with Analysis Workflow

These scripts are designed to work with AutoDense's optimization system:
- Tests the same make targets used in normal operation
- Uses actual configuration files
- Generates outputs in standard formats
- Compatible with existing analysis pipelines

The results provide a comprehensive baseline for parameter sensitivity analysis and system validation.
