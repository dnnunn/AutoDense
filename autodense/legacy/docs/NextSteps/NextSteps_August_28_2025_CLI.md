# Next Steps - August 28, 2025 (CLI Integration)

> **Doc Meta**  
> - **Purpose:** Priority tasks following AutoDense CLI real analysis integration completion
> - **Scope:** Testing, validation, optimization integration, and Maven dependency management
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-28

## 🎯 High Priority Tasks

### 1. **End-to-End CLI Testing with Real Images**
- **Priority**: HIGH
- **Task**: Test updated CLI with actual gel and colony images  
- **Commands to run**:
  ```bash
  make tune-etbr-ij INPUT=tmp/etbr_gel.jpg    # Should detect 20 lanes, not fake 6
  make tune-colony-ij INPUT=tmp/colon_plate.jpg  # Should count real colonies
  make tune-sds-ij INPUT=tmp/sds_gel.jpg      # Should detect actual bands
  ```
- **Expected outcomes**:
  - Real lane/colony/band counts (not fake data)
  - Generated annotated PNG files with proper overlays
  - Schema-compliant run_report.json with actual metrics
- **Validation**: Compare detected features with visual inspection of images

### 2. **Maven Dependency Management**
- **Priority**: HIGH  
- **Issue**: SnakeYAML dependency may not be in Maven POM
- **Task**: Add SnakeYAML to `autodense/plugin/pom.xml` if not already present:
  ```xml
  <dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.2</version>
  </dependency>
  ```
- **Verification**: Run `mvn compile` to ensure clean compilation

### 3. **Challenge Pack Configuration Validation**
- **Priority**: MEDIUM  
- **Task**: Verify config parameter names in challenge packs match CLI parameter extraction
- **Files to check**:
  - `challenge_packs/sds_page_v1/spec.yaml`  
  - `challenge_packs/colony_count_v1/spec.yaml`
  - `challenge_packs/etbr_v1/spec.yaml`
- **Verify parameters align**: 
  - SDS config: `detection.expected_lanes`, `detection.sensitivity`, `detection.constant_spacing`
  - Colony config: `colony_detection.stain`, `colony_detection.min_size`, `colony_detection.max_size`
  - EtBr config: `etbr_detection.expected_lanes`, `etbr_detection.sensitivity`

## 🔧 Medium Priority Tasks

### 4. **Real Metrics Extraction Verification**  
- **Task**: Validate that all three analysis types extract meaningful metrics from analysis results
- **Focus areas**:
  - **SDS-PAGE**: Ensure `ladder_r2`, `background_snr`, `band_stability_jitter` come from real analysis
  - **Colony**: Verify `colony_count`, `size_cv`, `touching_fraction` are calculated not estimated  
  - **EtBr**: Confirm `ladder_linear_r2`, `smearing_index`, `lane_count` reflect actual gel features
- **Method**: Run analysis and inspect values for reasonableness vs image content

### 5. **Headless Mode Validation**
- **Task**: Confirm headless operation works correctly in server environment
- **Test approach**:
  ```bash
  export DISPLAY=""  # Force headless environment
  make tune-colony-ij INPUT=tmp/colon_plate.jpg
  ```
- **Expected**: Analysis completes without GUI errors, generates PNG diagnostics

### 6. **Error Handling Testing**
- **Task**: Test CLI behavior with invalid inputs and configurations
- **Test cases**:
  - Non-existent image files
  - Malformed YAML configuration files  
  - Invalid parameter ranges
  - Missing required config sections
- **Expected**: Proper error messages and graceful failure with useful diagnostics

## 🚀 Enhancement Tasks

### 7. **Configuration Template Generation**
- **Task**: Create template config files for each analysis type showing all available parameters
- **Output files**:
  - `configs/sds_template.yaml` 
  - `configs/colony_template.yaml`
  - `configs/etbr_template.yaml`
- **Include**: Parameter descriptions, valid ranges, default values

### 8. **Optimization Loop Integration Testing**
- **Task**: Test complete autotune workflow with real CLI
- **Command**: Run full optimization with multiple parameter variations  
- **Validation**: Confirm optimization system receives real metrics and adjusts parameters appropriately

### 9. **Performance Benchmarking**
- **Task**: Measure CLI execution time for each analysis type
- **Method**: Time analysis with images of various sizes
- **Document**: Typical execution times and memory usage patterns

## 📋 Documentation Updates Needed

### 10. **Update CLAUDE.md**
- **Task**: Update project documentation to reflect completed CLI integration
- **Changes needed**:
  - Remove references to "placeholder implementations"
  - Document headless-safe visualization system  
  - Update architecture diagrams to show real analysis pipeline
  - Add environment automation section

### 11. **Create CLI Usage Guide**  
- **Task**: Document CLI usage patterns for developers and users
- **Include**:
  - Environment setup instructions
  - Configuration file format documentation
  - Expected output format and schema  
  - Troubleshooting common issues

## 🐛 Potential Issues to Monitor

### 12. **Memory Usage in Headless Mode**
- **Concern**: ImageJ operations may still allocate graphics memory even in headless mode
- **Monitor**: Memory usage during batch processing
- **Mitigation**: Add memory monitoring and cleanup if needed

### 13. **Configuration Parameter Validation**
- **Concern**: Invalid parameter ranges may not be properly validated before analysis
- **Task**: Add parameter range validation in CLI before passing to analysis tools
- **Implementation**: Use InputValidator patterns for numeric ranges

### 14. **Cross-Platform Compatibility**
- **Concern**: Environment scripts and paths may be macOS-specific  
- **Task**: Test on Linux/Windows if applicable
- **Areas**: File path handling, Java classpath construction, environment variables

## 🔄 Integration Dependencies

### 15. **Python Autotune System Updates**
- **Dependency**: Python optimization code may need updates to handle new schema format
- **Coordination**: Ensure Python side expects exact schema structure implemented  
- **Testing**: Validate end-to-end optimization loops work with real CLI output

### 16. **Build System Updates**
- **Task**: Update any CI/CD or build scripts that depend on CLI behavior
- **Changes**: Account for new dependencies, environment requirements, output formats

## ⏰ Timeline Recommendations

### **Immediate (Next Session)**:
1. End-to-end CLI testing with real images
2. Maven dependency verification  
3. Basic functionality validation

### **Short-term (Next 2-3 Sessions)**:
4. Configuration validation and parameter alignment
5. Real metrics extraction verification
6. Error handling robustness testing

### **Medium-term (Next Week)**:
7. Performance optimization and benchmarking
8. Documentation updates and usage guides  
9. Cross-platform compatibility testing

---

**Next session should prioritize functional testing to validate that the CLI integration works correctly with real analysis pipelines and produces meaningful optimization feedback.**