# Project Structure - August 28, 2025 - Evening Session

> **Doc Meta**
> - **Purpose:** Current project structure snapshot after CLI architecture fixes
> - **Scope:** Complete directory tree structure post YAML-based preprocessing parameter implementation
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-28

## Notable Changes Since Morning Session

### New Session Documentation
- **SessionSummaries/Session_summary_August_28_2025_CLI_Architecture.md** - Architecture completion session summary
- **NextSteps/NextSteps_August_28_2025_CLI_Architecture.md** - Debugging priorities for next session
- **Issues/Issues_August_28_2025_CLI_Architecture.md** - Technical debt and unresolved issues

### Modified Files
- **autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java** - Updated all analysis methods for YAML preprocessing
- **configs/*.yaml** - All three config files updated with complete preprocessing parameter structure

## Current Project Structure

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/
├── Architecture.md                          # Handle-based architecture documentation
├── CLAUDE.md                               # AI assistant session primer
├── COMPILE_PATHS.md                        # Build configuration paths
├── Makefile                                # Primary build automation
├── Makefile 2                              # Alternative build configuration
├── Makefile 3                              # Additional build targets
├── api-config.properties                   # API configuration file
├── audit.txt                               # System audit log
├── cspell.json                             # Spell checker configuration
├── docs_inventory.csv                      # Documentation inventory
├── markdownlint.config.json                # Markdown linting rules
├── pytest.ini                             # Python testing configuration
│
├── ChatGPT/                                # ChatGPT interaction logs
│   ├── Cleanup.md                          # Cleanup strategy documentation
│   └── What to fix (surgical and minimal).md # Targeted fix list
│
├── Issues/                                 # Issue tracking documents
│   ├── Issues_August_27_2025.md            # Previous session issues
│   ├── Issues_August_27_2025_Evening.md    # Evening session issues
│   ├── Issues_August_28_2025.md            # Current session issues (morning)
│   └── Issues_August_28_2025_CLI_Architecture.md # Current session issues (evening)
│
├── NextSteps/                              # Next steps planning
│   ├── ERROR_HANDLING_REFACTORING_GUIDE.md # Error handling guidelines
│   ├── INPUT_VALIDATION_SECURITY_GUIDE.md  # Security validation guide
│   ├── NextSteps_August_27_2025.md         # Previous priorities
│   ├── NextSteps_August_27_2025_Evening.md # Evening priorities
│   ├── NextSteps_August_28_2025.md         # Morning priorities
│   └── NextSteps_August_28_2025_CLI_Architecture.md # Evening priorities
│
├── SessionSummaries/                       # Session documentation
│   ├── Session_summary_August_20_2025.md   # Historical session
│   ├── Session_summary_August_25_2025.md   # Architecture restoration
│   ├── Session_summary_August_27_2025.md   # LLM toolkit development
│   ├── Session_summary_August_28_2025.md   # Morning CLI work
│   ├── Session_summary_August_28_2025_CLI_Integration.md # CLI integration
│   └── Session_summary_August_28_2025_CLI_Architecture.md # Architecture completion
│
├── StructureDocs/                          # Project structure snapshots
│   ├── Structure_August_28_2025.md         # Morning structure
│   └── Structure_August_28_2025_Evening.md # Current structure
│
├── archives/                               # Historical artifacts
├── audits/                                 # System audit results
│   ├── test_optimization/                  # Optimization validation
│   └── test_run/                           # Test execution results
│
├── autodense/                              # Core Java application
│   ├── README.md                           # Project README
│   ├── pom.xml                             # Maven build configuration
│   ├── build/                              # Build artifacts
│   ├── packaging/                          # Distribution packaging
│   │   └── resources/                      # Application resources
│   │       ├── AutoDense.app/              # macOS application bundle
│   │       ├── Fiji.app/                   # Fiji/ImageJ integration
│   │       ├── icons/                      # Application icons
│   │       ├── models/                     # ML models directory
│   │       └── scripts/                    # Build scripts
│   └── plugin/                             # ImageJ plugin source
│       ├── pom.xml                         # Plugin build configuration
│       ├── src/main/java/                  # Java source code
│       │   └── com/betterdairy/autodense/ # Main package hierarchy
│       │       ├── analysis/               # Analysis algorithms
│       │       ├── cli/                    # Command-line interface
│       │       │   └── AutotuneAnalysisCLI.java # **MODIFIED** - YAML integration
│       │       ├── model/                  # Data models
│       │       ├── session/                # Session management
│       │       ├── tools/                  # Analysis tools
│       │       └── util/                   # Utility classes
│       └── src/main/resources/             # Resource files
│           ├── config/                     # Configuration files
│           └── web/                        # Web interface assets
│
├── autodense_autotune/                     # Python optimization engine
│   ├── __init__.py                         # Package initialization
│   ├── adapters.py                         # Adapter interfaces
│   ├── cli.py                              # Command-line interface
│   ├── helper.py                           # Helper utilities
│   ├── java_bridge.py                      # Java-Python bridge
│   ├── patcher.py                          # Parameter patching
│   ├── persistence.py                      # State persistence
│   ├── runner.py                           # Optimization runner
│   ├── runners.py                          # Multiple runners
│   └── schemas.py                          # Data schemas
│
├── challenge_packs/                        # Task-specific optimization configs
│   ├── colony_count_v1/                    # Colony counting challenge
│   ├── etbr_v1/                           # EtBr agarose challenge
│   └── sds_page_v1/                       # SDS-PAGE challenge
│
├── configs/                                # **UPDATED** - YAML configuration files
│   ├── colony.yaml                         # **MODIFIED** - Full preprocessing params
│   ├── etbr.yaml                          # **MODIFIED** - Full preprocessing params
│   └── sds.yaml                           # **MODIFIED** - Full preprocessing params
│
├── docs/                                   # Documentation directory
│   ├── API_REFERENCE.md                    # Technical API documentation
│   ├── CHANGELOG.md                        # Version history
│   ├── DOCUMENT_CATALOG.md                 # Master documentation catalog
│   ├── README.md                           # Documentation navigation
│   └── [50+ other documentation files]     # Comprehensive docs
│
├── output/                                 # Analysis output directory
│   ├── colony_test/                        # Colony analysis results
│   ├── etbr_test/                          # EtBr analysis results
│   └── sds_test/                           # SDS analysis results
│
├── prompts/                                # LLM prompt templates
│   ├── README.md                           # Prompt documentation
│   ├── helper.system.md                    # Helper system prompt
│   └── orchestrator.system.md              # Orchestrator system prompt
│
├── scripts/                                # Utility scripts
│   ├── build_classpath.sh                  # Build path configuration
│   ├── docs_inventory.py                   # Documentation analysis
│   ├── env_check.py                        # Environment validation
│   └── [15+ other utility scripts]         # Build and maintenance tools
│
├── server/                                 # Server configuration
│   └── registry.json                       # Service registry
│
├── test_images/                            # Test image assets
│   └── colony_plate.jpg                    # Sample colony plate
│
├── tests/                                  # Python test suite
│   ├── __init__.py                         # Test package init
│   ├── test_helper_gate.py                 # Helper validation tests
│   ├── test_metrics_colony.py              # Colony metrics tests
│   └── test_schemas.py                     # Schema validation tests
│
└── tmp/                                    # Temporary files
    ├── synthetic_colony_plate.jpg          # Generated test images
    ├── synthetic_etbr_gel.jpg              # Generated test images
    └── synthetic_sds_gel.jpg               # Generated test images
```

## Key Directory Functions

### Core Application (`autodense/`)
- **Java-based ImageJ plugin** with Maven build system
- **CLI integration** via `AutotuneAnalysisCLI.java` (recently updated)
- **Analysis tools** for gel densitometry and colony counting
- **Session management** with handle-based architecture

### Optimization Engine (`autodense_autotune/`)
- **Python-based optimization system** with Java bridge
- **Challenge packs** for task-specific parameter tuning
- **Persistence layer** for optimization state management

### Configuration (`configs/`)
- **YAML-based parameter files** (newly implemented)
- **Task-specific configurations** for different analysis types
- **Preprocessing parameter control** via YAML structure

### Documentation (`docs/`, session folders)
- **Comprehensive technical documentation** (70+ files)
- **Session tracking** with summaries, issues, and next steps
- **Architecture documentation** with implementation guides

### Development Support
- **Build system** via Makefiles and Maven
- **Testing framework** with Python and Java test suites  
- **Utility scripts** for maintenance and analysis
- **Development environment** configuration files

## Architecture Status

- ✅ **YAML-based preprocessing** architecture complete
- ✅ **Handle-based session management** stable
- ✅ **Python-Java bridge** integrated
- 🔄 **Preprocessing debugging** ready for systematic testing
- ❌ **Image rotation issues** unresolved (awaiting debugging)

---

*Structure reflects completed architecture fixes - ready for preprocessing debugging phase.*