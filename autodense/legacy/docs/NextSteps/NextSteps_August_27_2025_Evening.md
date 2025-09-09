# Next Steps - August 27, 2025 (Evening Session)

> **Doc Meta**
> - **Purpose:** Priority tasks for completing PMD complexity and error handling work
> - **Scope:** Immediate next steps for Java code refactoring and consistency
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-27

## 🎯 High Priority Tasks

### 1. Address PMD Complexity Issues (Original Goal)
**Status**: Incomplete - needs decision and focused completion

**Approach Options:**
- **Option A**: Complete method complexity refactoring
  - Analyze remaining complex methods with current working directory context
  - Use Extract Method pattern systematically
  - Test PMD after each method refactoring
  - Enable PMD in pom.xml only after confirming timeout resolved

- **Option B**: Accept current state and document decision  
  - Document reasons PMD complexity checks are disabled
  - Set complexity threshold higher if needed
  - Focus on other code quality measures

**Recommendation**: Choose Option A with time-boxed approach (2 hours max)

**Dependencies**: None - all tools available

### 2. Standardize Error Handling Across Classes
**Status**: Partially complete - inconsistent implementation

**Current State:**
- `AssayOps.java`: Uses exception throwing ✅ 
- `ColonyAnalysisTools.java`: Uses improved error() method with logging ✅
- `GelAnalysisTools.java`: Needs review for consistency ❓

**Approach:**
1. **First**: Audit all three tool classes for error handling patterns
2. **Second**: Choose consistent approach:
   - Either standardize on exceptions (like AssayOps)  
   - Or standardize on JSONObject errors (like ColonyAnalysisTools)
   - Or document why mixed approach is intentional
3. **Third**: Implement chosen approach consistently

**Dependencies**: Logger type compatibility analysis

## 🔧 Medium Priority Tasks

### 3. Review GelAnalysisTools.java Error Handling
- Ensure consistency with chosen error handling pattern
- Check if it uses ErrorHandler, exceptions, or JSONObject errors
- Update to match standardized approach

### 4. Complete Todo List Cleanup
- Remove `.todos.md` file if no longer needed
- Ensure all actual todos are captured in issues or next steps
- Clean up temporary development artifacts

## 📋 Process Improvements

### 5. Implement Single-Task Focus Rule
**Learning**: User feedback highlighted scattered approach problem

**Implementation:**
- Before starting any refactoring, write clear single-task goal
- Complete that task fully before considering others
- Use TodoWrite tool to track exactly one task at a time
- Get user confirmation before switching focus

### 6. Pre-analyze Dependencies
- Before attempting integration changes (like ErrorHandler), analyze:
  - Logger types across classes  
  - Return type patterns
  - Import requirements
  - Compilation dependencies

## 🐛 Technical Debt Items

### 7. Improve Multi-LLM Audit Setup
**Issue**: Audit tool setup was confusing and error-prone

**Next Steps:**
- Create clearer setup guide for audit tool
- Fix GROK_API_KEY vs XAI_API_KEY configuration issue
- Test audit setup on fresh environment
- Document common setup problems and solutions

### 8. Maven PMD Configuration Review
- Review current PMD rules and thresholds
- Consider whether complexity thresholds are appropriate
- Document PMD configuration decisions
- Test with smaller complexity limits if needed

## ⚠️ Blockers to Address

### Logger Type Standardization
**Blocker**: ErrorHandler expects SessionLogger but tool classes use java.util.logging.Logger

**Resolution Options:**
1. Modify ErrorHandler to accept java.util.logging.Logger
2. Update tool classes to use SessionLogger  
3. Create adapter between logger types
4. Accept current mixed error handling approach

**Recommendation**: Investigate Option 1 first (modify ErrorHandler)

## 📅 Suggested Session Structure

### Next Session Plan (2-3 hours)
1. **Hour 1**: PMD complexity analysis and method refactoring
   - Focus on 1-2 most complex methods only
   - Test PMD after each change
   - Stop if no progress after 1 hour

2. **Hour 2**: Error handling standardization
   - Choose consistent approach across all tool classes
   - Implement chosen approach systematically
   - Test compilation after each change

3. **Hour 3**: Cleanup and verification
   - Run full compilation and tests
   - Verify no regressions introduced
   - Document final decisions made

### Success Criteria
- [ ] Either PMD complexity resolved OR decision documented to accept current state
- [ ] Consistent error handling approach across all tool classes
- [ ] All code compiles without errors
- [ ] No functional regressions
- [ ] Single-task focus maintained throughout

## 🔗 Related Documentation
- Session Summary: `/SessionSummaries/Session_summary_August_27_2025.md`
- Issues: `/Issues/Issues_August_27_2025_Evening.md`
- Error Handling Guide: `/NextSteps/ERROR_HANDLING_REFACTORING_GUIDE.md`