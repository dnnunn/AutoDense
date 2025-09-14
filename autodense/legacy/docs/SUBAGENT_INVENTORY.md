# AutoDense Subagent Inventory & Deployment Guide

> **Doc Meta**
> - **Purpose:** Comprehensive inventory of available subagents with utilities and startup guidance
> - **Scope:** All specialized agents created for AutoDense development, debugging, and optimization
> - **Owner:** @claudecode
> - **Last-verified:** 2025-01-02

## 🎯 Quick Reference

**Total Subagents:** 7  
**Categories:** Java Architecture (1), Python Development (1), Frontend (1), Scientific Analysis (1), Quality Assurance (2), AI Orchestration (1), Real-time Guidance (1)

---

## 📋 Complete Subagent Registry

### **1. java-architect**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/javatools/java-architect.md`
- **Category:** Core Architecture
- **Primary Utility:** Enterprise Java concurrency, Spring Boot optimization, performance troubleshooting
- **Key Capabilities:**
  - Context pool architecture design
  - Thread safety issue resolution  
  - Resource management optimization
  - Enterprise design pattern implementation
  - Production readiness assessment
- **When to Use:**
  - Java compilation errors or performance issues
  - Concurrency problems in optimization loops
  - Spring Boot configuration challenges
  - Enterprise architecture decisions
  - Memory leak investigation

### **2. imagej-operator**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/imagej-operator.md`
- **Category:** Scientific Analysis
- **Primary Utility:** ImageJ/Fiji macro execution, scientific image processing workflows
- **Key Capabilities:**
  - Lane-wise background model enforcement
  - Marker ladder detection (color/kDa positions)
  - Colony classifier with size/density normalization
  - Reproducible macro invocation with versioned YAML
  - QC report generation with overlays
- **When to Use:**
  - Scientific analysis workflow design
  - ImageJ macro debugging or optimization
  - Image processing parameter validation
  - Scientific overlay generation
  - Reproducibility issues in analysis

### **3. orchestrator-sheriff**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/orchestrator-sheriff.md`
- **Category:** AI Safety & Orchestration
- **Primary Utility:** Registry-first planning, tool-call hygiene, safe agent handoffs
- **Key Capabilities:**
  - Registry verification before tool usage
  - Structured handoff packet generation
  - Plan validation with pre/post conditions
  - Resource constraint enforcement
  - Rollback plan creation
- **When to Use:**
  - Complex multi-agent workflows
  - Tool registry validation issues
  - Agent coordination challenges
  - Workflow safety concerns
  - Task planning and execution oversight

### **4. vision-engineer**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/vision-engineer.md`
- **Category:** Vision & AI Optimization
- **Primary Utility:** Image preprocessing, ROI tiling, prompt template optimization for AI models
- **Key Capabilities:**
  - Token-efficient image preprocessing (75% reduction target)
  - ROI proposal with overlap management
  - Scientific feature preservation during optimization
  - Prompt template engineering with JSON schemas
  - Calibration set generation (3-5 images covering failure modes)
- **When to Use:**
  - AI model token optimization
  - Image preprocessing pipeline design
  - Vision model prompt engineering
  - Large image handling strategies
  - AI integration performance issues

### **5. audit-critic**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/audit-critic.md`
- **Category:** Quality Assurance & Security
- **Primary Utility:** Adversarial testing, vulnerability assessment, edge case generation
- **Key Capabilities:**
  - Brittle assumption detection
  - Silent failure mode identification
  - Risk scoring (likelihood × impact)
  - Minimal reproduction case creation
  - Test gap analysis with executable stubs
- **When to Use:**
  - Pre-production security review
  - Edge case testing scenarios
  - Code quality assessment
  - Vulnerability scanning
  - Test coverage enhancement

### **6. python-lab-pro**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/python-lab-pro.md`
- **Category:** Python Development
- **Primary Utility:** Python 3.11+ lab pipeline engineering, scientific data processing
- **Key Capabilities:**
  - PyImageJ/Fiji bridge development
  - OpenCV/NumPy scientific processing
  - CSV/Parquet I/O with metadata preservation
  - Reproducible pipeline creation with deterministic seeds
  - CLI/API surface development
- **When to Use:**
  - Python pipeline development
  - Scientific data processing
  - ImageJ-Python integration
  - Reproducibility issues
  - Lab workflow automation

### **7. ui-minimalist**
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/ui-minimalist.md`
- **Category:** Frontend Development
- **Primary Utility:** Accessible React + Tailwind UI components, WCAG compliance
- **Key Capabilities:**
  - Production-ready React components
  - WCAG 2.1 AA accessibility compliance
  - Design token system implementation
  - Responsive design (360px→768px→1280px)
  - Scientific UI patterns (data tables, file uploads, result dashboards)
- **When to Use:**
  - UI component development
  - Accessibility compliance issues
  - Frontend architecture decisions
  - Scientific data visualization
  - User interface optimization

### **8. autodense-coach** ⭐ *New*
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.claude/agents/autodense-coach.md`
- **Category:** Real-time Guidance & Quality Assurance
- **Primary Utility:** Real-time analysis quality monitoring, one-click parameter optimization
- **Key Capabilities:**
  - SDS-PAGE/EtBr/Colony quality detection
  - Confidence-based hint generation
  - One-click parameter fixes with reversible actions
  - Measurement integrity preservation
  - Scientific workflow optimization
- **When to Use:**
  - Real-time analysis monitoring
  - Quality assurance implementation
  - Parameter optimization guidance
  - Scientific measurement validation
  - User experience enhancement

---

## 🚀 Subagent Startup & Familiarization Function

```python
def initialize_subagent_ecosystem():
    """
    Startup function to familiarize with available subagents and determine optimal usage patterns
    """
    
    print("🔧 AutoDense Subagent Ecosystem Initialization")
    print("=" * 50)
    
    # Core Architecture Stack
    print("\n📐 CORE ARCHITECTURE:")
    print("├── java-architect: Enterprise Java, Spring Boot, concurrency")
    print("├── python-lab-pro: Scientific Python pipelines, reproducible workflows") 
    print("└── ui-minimalist: Accessible React components, scientific UI patterns")
    
    # Quality & Safety Layer
    print("\n🛡️ QUALITY & SAFETY:")
    print("├── orchestrator-sheriff: Tool registry safety, agent coordination")
    print("├── audit-critic: Adversarial testing, vulnerability assessment")
    print("└── autodense-coach: Real-time quality guidance, parameter optimization")
    
    # Specialized Domain Experts
    print("\n🔬 DOMAIN SPECIALISTS:")
    print("├── imagej-operator: Scientific image analysis, macro execution")
    print("└── vision-engineer: AI model optimization, token efficiency")
    
    # Decision Matrix for Agent Selection
    decision_matrix = {
        "Java Issues": ["java-architect"],
        "Python Development": ["python-lab-pro"],
        "UI/Frontend": ["ui-minimalist"],
        "Scientific Analysis": ["imagej-operator", "autodense-coach"],
        "AI/Vision Optimization": ["vision-engineer"],
        "Quality Assurance": ["audit-critic", "autodense-coach"],
        "Multi-Agent Coordination": ["orchestrator-sheriff"],
        "Real-time Guidance": ["autodense-coach"]
    }
    
    print("\n🎯 USAGE DECISION MATRIX:")
    for problem_type, agents in decision_matrix.items():
        agent_list = ", ".join(agents)
        print(f"  {problem_type:<25} → {agent_list}")
    
    # Proactive Usage Recommendations
    print("\n⚡ PROACTIVE USAGE PATTERNS:")
    print("  • Before coding: orchestrator-sheriff (planning)")
    print("  • During development: domain specialists (implementation)")
    print("  • After implementation: audit-critic (testing)")
    print("  • During analysis: autodense-coach (real-time guidance)")
    print("  • Before deployment: java-architect (production readiness)")
    
    return decision_matrix

def determine_optimal_agent(task_description: str, context: dict) -> list:
    """
    Determine which subagents to use based on task and context
    
    Args:
        task_description: Natural language description of the task
        context: Dictionary with keys like 'language', 'domain', 'phase', 'urgency'
    
    Returns:
        List of recommended subagent names in order of priority
    """
    
    recommendations = []
    
    # Language/Technology Detection
    if any(keyword in task_description.lower() for keyword in ['java', 'spring', 'concurrency', 'thread']):
        recommendations.append('java-architect')
    
    if any(keyword in task_description.lower() for keyword in ['python', 'numpy', 'opencv', 'imagej']):
        recommendations.append('python-lab-pro')
    
    if any(keyword in task_description.lower() for keyword in ['react', 'ui', 'component', 'accessibility']):
        recommendations.append('ui-minimalist')
    
    # Domain Detection
    if any(keyword in task_description.lower() for keyword in ['sds-page', 'gel', 'colony', 'analysis', 'imagej']):
        if 'real-time' in task_description.lower() or 'quality' in task_description.lower():
            recommendations.append('autodense-coach')
        recommendations.append('imagej-operator')
    
    if any(keyword in task_description.lower() for keyword in ['vision', 'ai model', 'token', 'prompt']):
        recommendations.append('vision-engineer')
    
    # Phase-Based Detection
    if context.get('phase') == 'testing' or 'test' in task_description.lower():
        recommendations.append('audit-critic')
    
    if context.get('phase') == 'planning' or 'coordinate' in task_description.lower():
        recommendations.append('orchestrator-sheriff')
    
    # Quality/Safety Detection
    if any(keyword in task_description.lower() for keyword in ['quality', 'guidance', 'parameter', 'optimization']):
        recommendations.append('autodense-coach')
    
    if any(keyword in task_description.lower() for keyword in ['security', 'vulnerability', 'edge case']):
        recommendations.append('audit-critic')
    
    # Remove duplicates while preserving order
    seen = set()
    unique_recommendations = []
    for agent in recommendations:
        if agent not in seen:
            seen.add(agent)
            unique_recommendations.append(agent)
    
    return unique_recommendations

# Usage Examples
USAGE_EXAMPLES = {
    "Java concurrency error in optimization loop": {
        "primary": "java-architect",
        "rationale": "Enterprise Java expert handles concurrency issues and optimization performance"
    },
    "Need to create scientific data visualization": {
        "primary": "ui-minimalist", 
        "secondary": "python-lab-pro",
        "rationale": "UI expert for components, Python expert for data processing"
    },
    "Analysis results look suspicious": {
        "primary": "autodense-coach",
        "secondary": "imagej-operator", 
        "rationale": "Coach provides real-time quality guidance, operator handles detailed analysis"
    },
    "Preparing for production deployment": {
        "primary": "audit-critic",
        "secondary": "java-architect",
        "rationale": "Critic finds vulnerabilities, architect ensures production readiness"
    },
    "Complex multi-step workflow planning": {
        "primary": "orchestrator-sheriff",
        "rationale": "Sheriff ensures safe coordination and proper tool usage validation"
    }
}

if __name__ == "__main__":
    decision_matrix = initialize_subagent_ecosystem()
    
    print("\n🧪 TESTING DECISION LOGIC:")
    test_tasks = [
        "Fix Java memory leak in context pool",
        "Create accessible data table component", 
        "Optimize AI model token usage",
        "Analyze suspicious gel band detection"
    ]
    
    for task in test_tasks:
        agents = determine_optimal_agent(task, {'phase': 'development'})
        print(f"  '{task}' → {agents}")
```

---

## 🎯 When to Use Each Agent

### **Development Phase Mapping:**

| **Phase** | **Primary Agents** | **Secondary Agents** |
|-----------|-------------------|---------------------|
| **Planning** | orchestrator-sheriff | autodense-coach |
| **Architecture** | java-architect, python-lab-pro | ui-minimalist |
| **Implementation** | Domain specialists | orchestrator-sheriff |
| **Testing** | audit-critic | autodense-coach |
| **Optimization** | autodense-coach, vision-engineer | All specialists |
| **Production** | java-architect, audit-critic | orchestrator-sheriff |

### **Problem Type Mapping:**

| **Problem Type** | **Agent Selection** | **Escalation Path** |
|-----------------|-------------------|-------------------|
| **Compilation Errors** | java-architect → python-lab-pro → ui-minimalist |
| **Performance Issues** | autodense-coach → java-architect → vision-engineer |
| **Quality Concerns** | autodense-coach → audit-critic → imagej-operator |
| **Integration Issues** | orchestrator-sheriff → domain specialists |
| **User Experience** | ui-minimalist → autodense-coach → python-lab-pro |

### **Urgency-Based Selection:**

- **🚨 Critical Production Issue:** java-architect + audit-critic (parallel)
- **⚡ Real-time Analysis Problem:** autodense-coach (immediate)
- **🔧 Development Blocker:** Domain specialist + orchestrator-sheriff
- **📊 Performance Optimization:** vision-engineer + autodense-coach
- **🎯 Quality Improvement:** audit-critic + autodense-coach

---

## 💡 Best Practices

### **Agent Coordination:**
1. **Always start with orchestrator-sheriff** for complex multi-agent tasks
2. **Use autodense-coach proactively** during any scientific analysis
3. **Parallel delegation** for independent concerns (UI + backend)
4. **Sequential delegation** for dependent tasks (architecture → implementation → testing)

### **Efficiency Guidelines:**
- **Single domain issues:** Use specific domain expert
- **Cross-domain issues:** Start with orchestrator-sheriff
- **Quality assurance:** Always include audit-critic or autodense-coach
- **Production readiness:** Always include java-architect for final review

### **Common Anti-Patterns:**
- ❌ Using generic agents for specialized scientific tasks
- ❌ Skipping quality agents (audit-critic, autodense-coach) before deployment
- ❌ Using single agent for clearly multi-domain problems
- ❌ Not leveraging autodense-coach for real-time guidance opportunities

---

This inventory provides a comprehensive guide for optimal subagent utilization throughout the AutoDense development lifecycle. The startup function familiarizes you with capabilities, while the decision matrices ensure appropriate agent selection for maximum effectiveness.