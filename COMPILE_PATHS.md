# AutoDense Compilation Paths Reference

> **⚠️ CRITICAL:** Always read [ENVIRONMENT_SETUP.md](ENVIRONMENT_SETUP.md) first for Python environment setup!

## Current Working Directory Structure
```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/           <- ROOT (for make commands)
├── Makefile                                                   <- make tune-etbr-ij, etc.
├── autodense/
│   └── plugin/                                                <- PLUGIN DIR (for mvn compile)
│       ├── pom.xml
│       └── src/main/java/...
├── configs/
└── output/
```

## Key Commands by Directory

### From ROOT (`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/`):
- `make tune-etbr-ij INPUT=tmp/etbr_gel.jpg` - Run EtBr analysis
- `make tune-colony-ij INPUT=tmp/colony_plate.jpg` - Run colony analysis  
- `make tune-sds-ij INPUT=tmp/sds_gel.jpg` - Run SDS-PAGE analysis

### From PLUGIN DIR (`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`):
- `mvn compile -q` - Compile Java code
- `mvn clean compile -q` - Clean and compile

## Navigation Commands (SINGLE COMMAND PATTERN)
```bash
# Go to ROOT with environment setup (RECOMMENDED)
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense && source .venv/bin/activate && pwd

# Go to plugin dir with environment
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense && source .venv/bin/activate && cd autodense/plugin && pwd
```

## Python Environment Integration
```bash
# ALWAYS start with environment verification
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense && source .venv/bin/activate && python verify_environment.py

# For Python + Java workflows
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense && source .venv/bin/activate && cd autodense/plugin && mvn compile -q
```

**📖 Complete environment documentation: [ENVIRONMENT_SETUP.md](ENVIRONMENT_SETUP.md)**