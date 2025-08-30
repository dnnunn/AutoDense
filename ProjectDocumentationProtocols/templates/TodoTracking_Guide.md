# Todo Tracking and Task Management Guide

> **Doc Meta**
> - **Purpose:** Systematic task management and todo tracking procedures for development sessions
> - **Scope:** Task creation, prioritization, tracking, and completion workflows
> - **Owner:** @[PROJECT_OWNER]
> - **Last-verified:** [CURRENT_DATE]

## 🎯 Task Management Philosophy

### Core Principles
- **Granular tasks:** Break complex work into specific, actionable items
- **Clear ownership:** Every task has a clear responsible party
- **Status visibility:** Current progress is always transparent
- **Priority-driven:** Work highest-impact items first
- **Regular cleanup:** Remove completed/obsolete tasks promptly

### Task States
- **pending:** Task identified but not yet started
- **in_progress:** Currently being worked on (limit to 1-2 tasks)
- **completed:** Task finished successfully
- **blocked:** Cannot proceed due to external dependency
- **cancelled:** Task no longer needed or relevant

## 📋 Todo Creation Guidelines

### Task Description Format
```markdown
**Imperative form:** "Fix authentication bug in user login"
**Present continuous form:** "Fixing authentication bug in user login"
```

### Task Quality Standards
- **Specific:** Clear what needs to be accomplished
- **Actionable:** Can be completed in one session or clearly broken down
- **Measurable:** Success criteria are obvious
- **Time-bounded:** Reasonable scope for available time

### Examples

#### ✅ Well-Written Tasks
- **Good:** "Add input validation to user registration form"
  - **Active form:** "Adding input validation to user registration"
- **Good:** "Fix memory leak in image processing pipeline"
  - **Active form:** "Fixing memory leak in image processing"
- **Good:** "Write unit tests for authentication service"
  - **Active form:** "Writing unit tests for authentication service"

#### ❌ Poorly-Written Tasks
- **Bad:** "Work on frontend" (too vague)
- **Bad:** "Fix bugs" (not specific)
- **Bad:** "Improve performance" (not measurable)
- **Bad:** "Update documentation eventually" (no urgency)

## 🔄 Task Lifecycle Management

### Task Creation Triggers
- **User requests:** New feature or bug fix requests
- **Code review findings:** Issues discovered during review
- **Technical debt:** Identified during development
- **Investigation results:** Follow-up work from research
- **Testing failures:** Issues found during QA

### Prioritization Framework

#### Priority Levels
1. **CRITICAL:** Blocks all other work, must be fixed immediately
2. **HIGH:** Important for current milestone, should be next
3. **MEDIUM:** Valuable but can be scheduled
4. **LOW:** Nice-to-have, address when convenient

#### Priority Assessment Questions
- Does this block other team members?
- Is this customer-facing and broken?
- Does this affect system stability or security?
- Is this required for the current sprint/milestone?
- What's the business impact of delay?

### Status Transitions

```
pending → in_progress → completed
   ↓           ↓
blocked    cancelled
```

#### Status Change Rules
- **pending → in_progress:** When you start working on task
- **in_progress → completed:** When task is fully finished and verified
- **in_progress → blocked:** When external dependency prevents progress
- **any → cancelled:** When task is no longer needed

## 🛠️ Implementation with AI Tools

### Using TodoWrite Tool
```markdown
[
  {
    "content": "Fix authentication timeout issue",
    "activeForm": "Fixing authentication timeout issue", 
    "status": "pending"
  },
  {
    "content": "Add error handling to payment service",
    "activeForm": "Adding error handling to payment service",
    "status": "in_progress"
  }
]
```

### Task Management Best Practices
- **One task in_progress:** Focus prevents context switching
- **Complete immediately:** Mark tasks done as soon as finished
- **Update status real-time:** Don't batch status updates
- **Remove completed:** Clean up finished tasks regularly

## 📊 Session Task Management

### Session Start Procedure
1. **Review existing tasks:** Check pending and blocked items
2. **Prioritize for session:** Select 2-3 tasks for current session
3. **Mark in_progress:** Update status for task you're starting
4. **Time-box work:** Estimate effort for selected tasks

### During Session
- **Stay focused:** Work one task at a time
- **Update status:** Change to completed immediately when done
- **Add new tasks:** Capture new work as it's discovered
- **Document blockers:** Note dependencies that prevent progress

### Session End Procedure
1. **Complete all finished tasks:** Mark everything completed
2. **Update remaining tasks:** Adjust priorities and status
3. **Create next session tasks:** Based on session discoveries
4. **Clean up cancelled:** Remove obsolete or irrelevant items

## 🎪 Task Categories and Templates

### Bug Fixes
```markdown
{
  "content": "Fix [specific bug description] in [component]",
  "activeForm": "Fixing [specific bug description] in [component]",
  "status": "pending"
}
```

### Feature Development
```markdown
{
  "content": "Implement [feature name] with [key requirements]", 
  "activeForm": "Implementing [feature name] with [key requirements]",
  "status": "pending"
}
```

### Testing
```markdown
{
  "content": "Write [test type] for [component/feature]",
  "activeForm": "Writing [test type] for [component/feature]", 
  "status": "pending"
}
```

### Documentation
```markdown
{
  "content": "Document [process/feature] in [location]",
  "activeForm": "Documenting [process/feature] in [location]",
  "status": "pending"  
}
```

### Refactoring
```markdown
{
  "content": "Refactor [component] to [improvement goal]",
  "activeForm": "Refactoring [component] to [improvement goal]",
  "status": "pending"
}
```

## 📈 Progress Tracking

### Daily Standup Integration
- **What I completed:** List completed tasks from yesterday
- **What I'm working on:** Current in_progress tasks
- **Blockers:** Any blocked tasks and what's needed

### Sprint/Milestone Tracking
- **Completed tasks:** Total finished work
- **Remaining tasks:** Pending work for current scope
- **Blocked items:** Dependencies preventing progress
- **New discoveries:** Tasks added during sprint

### Velocity Measurement
- **Tasks per session:** Average completion rate
- **Task accuracy:** How often estimates are correct
- **Blocking frequency:** How often work gets blocked
- **Completion percentage:** Ratio of finished to started tasks

## ⚠️ Common Pitfalls

### Task Management Anti-Patterns
- **Vague descriptions:** "Work on the thing"
- **Massive scope:** Tasks taking multiple days
- **Status staleness:** Not updating progress
- **Priority confusion:** Everything marked as critical
- **Context switching:** Multiple in_progress tasks

### Recovery Strategies
- **Task too big:** Break into smaller, specific sub-tasks
- **Progress unclear:** Add specific success criteria
- **Blocked too long:** Find alternative approaches or escalate
- **Priorities unclear:** Re-assess business impact and urgency

## 🔍 Quality Metrics

### Task Quality Indicators
- **Clear acceptance criteria:** Obvious when task is done
- **Reasonable scope:** Completable in single session or clearly decomposed
- **Proper prioritization:** Reflects actual business/technical urgency
- **Status accuracy:** Current status reflects reality

### Process Health Metrics
- **Completion rate:** Percentage of started tasks that get finished
- **Time accuracy:** How often task estimates are realistic
- **Staleness rate:** Percentage of old, unchanged tasks
- **Context switching:** Frequency of multiple in_progress tasks

---

## 🎯 Success Patterns

### Effective Todo Management
1. **Start sessions with todo review:** Load context immediately
2. **Maintain single focus:** One in_progress task at a time
3. **Complete immediately:** Mark done as soon as finished
4. **Capture everything:** Don't lose track of new discoveries
5. **Regular cleanup:** Remove completed/cancelled items

### Integration with Documentation
- **Session summaries:** Reference completed tasks
- **Next steps:** Convert high-priority todos to next session focus
- **Issues tracking:** Link bugs to specific todo items
- **Progress reports:** Use completion metrics for status updates

**Remember:** Effective task management reduces cognitive load and ensures important work doesn't get forgotten. The system should be lightweight enough to maintain but comprehensive enough to track all necessary work.