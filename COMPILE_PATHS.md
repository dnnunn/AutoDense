# AutoDense Compilation Paths Reference

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

## Navigation Commands
- `cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/` - Go to ROOT
- `cd autodense/plugin/` - From ROOT to plugin dir
- `cd ../..` - From plugin dir back to ROOT

## Current Session Tracking
- I am currently in: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
- To run make commands, I need: `cd ../..`
- To compile Java, I can run: `mvn compile -q` (from current dir)