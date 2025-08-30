# AutoDense Development Startup Guide

> **Doc Meta**
>
> - **Purpose:** Quick start guide for AutoDense development environment setup and workflow
> - **Scope:** Build commands, testing procedures, and development workflow
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## Read CLAUDEKIT_REFERENCE

* to familiarize yourself with tools, hooks and agents to help you in coding, troubleshooting, debugging and code hygiene.

  /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/docs/CLAUDEKIT_REFERENCE.md

## Read Latest Session Summary

/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/sessionsummaryQuick Start Commands

## Read Latest Next Steps

/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/NextSteps

## Read Latest Issues

/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/Issues

## Read Latest Project Structure

/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/StructureDocs

Commit this structure to your project memory and if you have issues finding files, subdirectories or paths, refer to it immediately and don't make guesses.

## Read Claude.md

# Ask whether to build and run Autodense

### 1. Build AutoDense

```bash
cd /path/to/AutoDense
mvn -q -DskipTests=true -f autodense/pom.xml -pl plugin -am clean install
```

### 2. Run AutoDense for Testing

```bash
mvn -q -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.plugin.OpenAnalyzeCommand \
  -Dexec.classpathScope=runtime
```

## Development Workflow

### After Code Changes:

1. **Kill existing processes** (if running):
   ```bash
   pkill -f ImageJ
   ```
2. **Rebuild**:
   ```bash
   mvn -q -DskipTests=true -f autodense/pom.xml -pl plugin -am clean install
   ```
3. **Restart**:
   ```bash
   mvn -q -f autodense/plugin/pom.xml exec:java \
     -Dexec.mainClass=com.betterdairy.autodense.plugin.OpenAnalyzeCommand \
     -Dexec.classpathScope=runtime
   ```
