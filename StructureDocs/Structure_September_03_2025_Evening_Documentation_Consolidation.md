# Project Structure - September 3, 2025 Evening: Post Documentation Consolidation

> **Doc Meta**
> - **Purpose:** Current project structure snapshot after documentation consolidation session
> - **Scope:** Complete directory tree with focus on documentation organization changes
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-03

## 🔄 Notable Structural Changes Since Last Snapshot

### Documentation Consolidation Impact
- **Archive System**: New `archive/` directory with deprecated content management
- **Session Documentation**: Added comprehensive wrap-up documentation per /wrapup protocols
- **Tombstone Documents**: DEFINITIVE_BUILD_REFERENCE.md and docs/New plan.md converted to deprecation notices

### Archive System Implementation
- **New Directory**: `/archive/` with staging and rotation capabilities
- **Tarball Management**: `deprecated-current.tar.gz` containing consolidated deprecated content
- **Index Tracking**: `INDEX.txt` for historical change management

## 📁 Complete Project Structure

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/
├── .audit_venv/                        # Python audit environment
├── .claude/                           # Claude Code configuration
│   ├── agents/                        # Custom agent definitions
│   ├── commands/                      # Custom commands
│   └── [configuration files]
├── .git/                              # Git repository data
├── .github/                           # GitHub configuration
│   └── workflows/                     # CI/CD workflows
├── .githooks/                         # Custom git hooks
├── .pytest_cache/                     # Python test cache
├── .venv/                             # Python 3.13 virtual environment
│   ├── bin/activate                   # Virtual environment activation
│   ├── lib/python3.13/site-packages/ # Python dependencies (numpy, scipy, yaml, etc.)
│   └── pyvenv.cfg                     # Virtual environment configuration
├── .vscode/                           # VS Code settings
│
├── api-config.properties              # Gemini API configuration
├── Architecture.md                    # Current system architecture documentation
├── BUILD_GUIDE.md                     # 📋 CONSOLIDATED BUILD REFERENCE (Enhanced)
├── DEFINITIVE_BUILD_REFERENCE.md      # ⚠️ DEPRECATED TOMBSTONE
├── PROJECT_STRUCTURE_REFERENCE.md     # Project structure and paths reference
├── COMPILE_PATHS.md                   # Compilation path documentation
├── AUDIT_SCRIPTS_README.md            # Audit tooling documentation
│
├── archive/                           # 🆕 DEPRECATED CONTENT ARCHIVE SYSTEM
│   ├── deprecated-current.tar.gz      # Single tarball archive
│   ├── INDEX.txt                      # Archive change tracking
│   ├── rotate_tarball.sh              # Archive rotation script
│   └── staging/                       # Temporary staging for deprecation
│
├── archives/                          # Legacy archive directory
│
├── autodense/                         # Java Maven project structure
│   ├── plugin/                        # 🎯 MAVEN WORKING DIRECTORY
│   │   ├── pom.xml                    # Maven configuration (Java 17)
│   │   ├── src/main/java/             # Java source code
│   │   │   ├── com/betterdairy/autodense/cli/
│   │   │   │   └── AutotuneAnalysisCLI.java    # Main CLI entry point
│   │   │   ├── com/betterdairy/autodense/core/
│   │   │   ├── com/betterdairy/autodense/util/
│   │   │   └── [other Java packages]
│   │   └── target/                    # 🔧 MAVEN BUILD OUTPUT
│   │       ├── classes/               # Compiled Java classes
│   │       ├── autodense-plugin-0.1.0-SNAPSHOT.jar
│   │       ├── classpath.txt          # Dependencies classpath
│   │       └── runtime-classpath.txt  # Complete runtime classpath
│   │
│   └── packaging/                     # Application packaging resources
│       └── resources/                 # Bundled resources
│           ├── Fiji.app/              # Fiji ImageJ distribution
│           └── AutoDense.app/         # macOS app bundle structure
│
├── autodense_autotune/                # 🐍 PYTHON OPTIMIZATION ENGINE
│   ├── __init__.py
│   ├── config_manager.py              # Configuration management
│   ├── java_bridge.py                 # Python-Java bridge (REQUIRES --no-exit)
│   ├── cli.py                         # Python CLI interface
│   └── [optimization modules]         # Parameter tuning and analysis
│
├── audits/                            # Analysis and audit results
│   ├── autotune_runs/                 # Optimization run outputs
│   └── [audit reports and data]
│
├── challenge_packs/                   # Task-specific optimization configurations
│   ├── sds_page_v1/                   # SDS-PAGE optimization spec
│   ├── colony_count_v1/               # Colony counting optimization spec
│   ├── etbr_v1/                       # EtBr gel optimization spec
│   └── [challenge specifications]
│
├── configs/                           # 📋 YAML ANALYSIS CONFIGURATIONS
│   ├── sds.yaml                       # SDS-PAGE analysis config
│   ├── colony.yaml                    # Colony counting config
│   ├── etbr.yaml                      # EtBr gel analysis config
│   └── sds_sensitive.yaml             # 12-lane SDS optimized config
│
├── docs/                              # 📚 PROJECT DOCUMENTATION
│   ├── CONSOLIDATION_PLAN.md          # 🆕 Documentation consolidation strategy
│   ├── CONTRADICTIONS.md              # 🆕 Content contradiction analysis
│   ├── DOCMAP.json                    # 🆕 Complete documentation inventory
│   ├── OVERLAP.md                     # 🆕 Content overlap analysis
│   ├── DEPRECATIONS.md                # 🆕 Deprecation registry
│   ├── New plan.md                    # ⚠️ DEPRECATED TOMBSTONE
│   ├── PROJECT_STATUS.md              # Current project status
│   ├── IMPLEMENTATION_PLAN.md         # Implementation roadmap
│   ├── MIGRATION.md                   # Architecture migration guide
│   ├── MIGRATION_NOTES.md             # Autotune migration details
│   ├── API_REFERENCE.md               # Tool interface documentation
│   ├── CANONICAL_TOOLS_SUMMARY.md     # Tool consolidation strategy
│   ├── TOOL_VALIDATION_SYSTEM.md      # Validation utilities
│   │
│   │ # Colony Analysis Documentation Cluster (CONSOLIDATION TARGET)
│   ├── STREAMLINED_COLONY_ANALYSIS_SYSTEM.md      # Comprehensive guide (SURVIVOR)
│   ├── COLONY_CLASSIFIER_IMPLEMENTATION.md        # Technical implementation
│   ├── COLONY_EXPORT_SYSTEM.md                    # Export functionality
│   ├── COLONY_IDENTIFICATION_ASSIST.md            # User interaction features
│   ├── COLONY_NORMALIZATION.md                    # Normalization algorithms
│   │
│   └── [additional documentation files]
│
├── Issues/                            # 📋 SESSION ISSUE TRACKING
│   ├── Issues_September_03_2025_Documentation_Consolidation.md  # 🆕 THIS SESSION
│   ├── Issues_August_30_2025_Configuration_Pipeline_Fix.md
│   └── [historical issue documentation]
│
├── NextSteps/                         # 🎯 SESSION PLANNING
│   ├── NextSteps_September_03_2025_Documentation_Consolidation.md  # 🆕 THIS SESSION
│   ├── NextSteps_August_30_2025_Configuration_Pipeline_Fix.md
│   └── [historical next steps documentation]
│
├── output/                            # Analysis output directory
│   ├── test/                          # Test output location
│   └── [analysis results]
│
├── ProjectDocumentationProtocols/     # Documentation standards and templates
│   ├── README.md                      # Protocol documentation
│   ├── STARTUP.md                     # Session startup procedures
│   ├── WRAPUP.md                      # Session wrap-up procedures
│   └── templates/                     # Document templates
│       ├── DocumentCatalog_Template.md
│       ├── NextSteps_Template.md
│       ├── DocMeta_Template.md
│       └── TodoTracking_Guide.md
│
├── prompts/                           # Gemini optimization prompts
│   ├── orchestrator.system.md         # Main orchestrator prompt
│   ├── helper.system.md               # Helper critic prompt
│   └── [prompt definitions]
│
├── samples/                           # 🖼️ TEST IMAGE SAMPLES
│   ├── sds_gel.jpg                    # Main SDS-PAGE test image (12 lanes)
│   ├── colony_plate.jpg               # Colony counting test image
│   ├── etbr_gel.jpg                   # EtBr gel test image
│   ├── synthetic_sds_gel2.png         # Generated SDS test
│   ├── synthetic_colony_plate2.png    # Generated colony test
│   └── [additional test samples]
│
├── SessionSummaries/                  # 📝 SESSION DOCUMENTATION
│   ├── Session_summary_September_03_2025_Documentation_Consolidation.md  # 🆕 THIS SESSION
│   ├── Session_summary_September_01_2025_Truth_Preservation_Analysis.md
│   ├── Session_summary_August_30_2025_Configuration_Pipeline_Fix.md
│   └── [historical session summaries]
│
├── StructureDocs/                     # 📁 PROJECT STRUCTURE SNAPSHOTS
│   ├── Structure_September_03_2025_Evening_Documentation_Consolidation.md  # 🆕 THIS SESSION
│   ├── Structure_September_01_2025_Evening_Phase_1_Complete.md
│   ├── Structure_August_30_2025_Configuration_Pipeline_Fix.md
│   └── [historical structure documentation]
│
├── build.sh                          # 🔨 UNIFIED BUILD SCRIPT (PRIMARY TOOL)
├── Makefile                           # Alternative build interface
├── .env                               # Environment configuration
├── .gitignore                         # Git ignore patterns
├── .gitattributes                     # Git file attributes
└── [configuration and utility files]
```

## 🔧 Build System Architecture

### Primary Build Tool
- **`build.sh`** - Unified build script supporting all operations
- **Command**: `./build.sh all` (STANDARDIZED - no more `build.sh build`)
- **Capabilities**: env-check, compile, jar, test-cli, test-python, package, clean

### Environment Dependencies
- **Java 17**: Required for Maven compilation and CLI execution
- **Python 3.13**: Virtual environment at `.venv/` with scientific stack
- **Maven**: Automatic dependency management and classpath generation

### Build Artifacts
- **JAR Location**: `autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
- **Classpaths**: Both `classpath.txt` and `runtime-classpath.txt` generated
- **Python Bridge**: Requires `--no-exit` flag for optimization loops

## 📊 Documentation Organization Status

### Recently Consolidated (This Session)
- **Build Documentation**: DEFINITIVE_BUILD_REFERENCE.md → BUILD_GUIDE.md
- **Architecture Planning**: Legacy docs/New plan.md → proper tombstone
- **Command Standardization**: All references now use `./build.sh all`

### Next Consolidation Targets (Identified)
- **Colony Analysis Cluster**: 5 documents with 65% reduction potential
- **Migration Guides**: 2 guides requiring unification  
- **API Documentation**: Multiple tool interface documents

### Archive System
- **Implementation**: Single tarball rotation (`deprecated-current.tar.gz`)
- **Tracking**: INDEX.txt maintains deprecation history
- **Staging**: Temporary area for content before archival

## 🐍 Python Environment

### Virtual Environment
- **Location**: `.venv/` (activated by build.sh automatically)
- **Python Version**: 3.13.4
- **Key Packages**: numpy, scipy, scikit-image, matplotlib, pyyaml

### Autotune System
- **Directory**: `autodense_autotune/`
- **Bridge**: `java_bridge.py` handles Python-Java communication
- **Critical**: Must use `--no-exit` flag for optimization loops

## ☕ Java Environment

### Maven Configuration
- **Working Directory**: `autodense/plugin/`
- **Java Version**: 17.0.15 
- **Main Class**: `com.betterdairy.autodense.cli.AutotuneAnalysisCLI`
- **Dependencies**: ImageJ/Fiji JARs, JSON, YAML libraries

## 📈 Documentation Health Metrics

### Current State (Post-Consolidation)
- **Total MD Files**: ~86 files across project
- **Doc Meta Compliance**: ~80% (improvement from ~60%)
- **Consolidation Progress**: Phase 1 complete, Phase 2 roadmap defined
- **Archive System**: Operational with proper historical tracking

### Quality Improvements
- **Single Build Authority**: BUILD_GUIDE.md as comprehensive reference
- **Eliminated Overlaps**: 2 redundant documents consolidated
- **Standardized Commands**: Consistent `./build.sh all` usage
- **Proper Deprecation**: Tombstone redirects with clear rationales

This structure reflects a mature development environment with comprehensive documentation standards, systematic consolidation processes, and sustainable maintenance practices established through the doc-condenser analysis and implementation.