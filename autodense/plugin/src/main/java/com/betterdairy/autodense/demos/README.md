# AutoDense Demo System

> **Doc Meta**
> - **Purpose:** Demo system organization and integration documentation
> - **Scope:** Demo structure, UI integration, and usage scenarios
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

Organized demonstration files for user experience enhancement.

## Directory Structure

```
demos/
├── gel-analysis/          # Gel electrophoresis workflows
│   ├── SyntheticGelDemo.java
│   ├── BandAssistDemo.java
│   └── ProteinQuantDemo.java
├── colony-analysis/       # Colony counting and classification
│   ├── ColonyClassifierDemo.java
│   └── ColonyDetectorDemo.java
├── time-series/          # Growth tracking over time
│   └── TimeSeriesDemo.java
├── integration/          # Complete pipeline demonstrations
│   └── IntegrationDemo.java
└── performance/          # Speed and accuracy testing
    └── PerformanceDemo.java
```

## Integration with UI

- **Demo Menu**: Accessible via Help > Demos submenu
- **Interactive Tutorials**: Click-to-run with guided explanations
- **Progress Tracking**: Visual indicators for demo completion
- **Sample Data**: Bundled synthetic images for consistent testing

## Usage Scenarios

1. **New User Onboarding**: Sequential tutorial progression
2. **Feature Exploration**: Targeted demonstrations of specific capabilities
3. **Troubleshooting**: Reference implementations for bug reports
4. **Academic Teaching**: Classroom-ready examples with expected outputs
5. **Quality Assurance**: Regression testing during development
