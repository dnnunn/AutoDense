# Environment Setup: [PROJECT_NAME]

> **Doc Meta**
> - **Purpose:** Critical environment procedures for [PROJECT_NAME] development setup
> - **Scope:** Complete environment configuration, dependencies, and verification procedures
> - **Owner:** @[PROJECT_OWNER]
> - **Last-verified:** [CURRENT_DATE]

## 🚨 CRITICAL: Environment Setup (PREVENT SESSION TIME WASTE)

**These procedures prevent wasting 20+ minutes every session rediscovering environment setup:**

### Environment Requirements
- **Operating System:** [OS_REQUIREMENTS] 
- **Runtime Version:** [RUNTIME_AND_VERSION] (e.g., Java 17, Python 3.9+, Node 18+)
- **Build Tool:** [BUILD_TOOL_AND_VERSION] (e.g., Maven 3.8+, npm 8+, Cargo 1.60+)
- **Additional Dependencies:** [OTHER_DEPENDENCIES]

### Absolute Paths (MEMORIZE THESE)
- **Project Root:** `/absolute/path/to/[PROJECT_NAME]`
- **Environment Config:** `/absolute/path/to/[ENV_CONFIG_LOCATION]`
- **Build Directory:** `/absolute/path/to/[BUILD_DIRECTORY]`
- **Source Directory:** `/absolute/path/to/[SOURCE_DIRECTORY]`
- **Test Directory:** `/absolute/path/to/[TEST_DIRECTORY]`

### Environment Activation (EXACT COMMANDS)
```bash
# Navigate to project root
cd /absolute/path/to/[PROJECT_NAME]

# Environment activation (choose your stack)
[ENVIRONMENT_ACTIVATION_COMMAND]

# Examples for different stacks:
# Python: source .venv/bin/activate
# Node.js: source ~/.nvm/nvm.sh && nvm use
# Java: export JAVA_HOME=/path/to/java17
# Rust: source ~/.cargo/env
# Ruby: source ~/.rvm/scripts/rvm && rvm use
# Go: export GOROOT=/path/to/go && export PATH=$GOROOT/bin:$PATH

# Load environment variables if needed
[ENV_VAR_LOADING_COMMAND]
# Examples:
# source .env
# export $(cat .env | xargs)
# set -a; source .env; set +a
```

### Verification Commands (RUN EVERY SESSION START)
```bash
# Verify runtime version
[RUNTIME] --version
# Examples: java -version, python --version, node --version, go version

# Verify build tool
[BUILD_TOOL] --version  
# Examples: mvn --version, npm --version, cargo --version

# Check critical dependencies
[DEPENDENCY_CHECK_COMMAND]
# Examples:
# which docker && docker --version
# which postgres && psql --version
# which redis-server && redis-server --version

# Verify project dependencies
[PROJECT_DEPENDENCY_CHECK]
# Examples:
# mvn dependency:resolve
# npm list --depth=0
# pip list | grep [critical-package]
# cargo check --quiet
```

### Build System Setup (EXACT SEQUENCE)
```bash
# Clean any previous builds
[CLEAN_COMMAND]
# Examples: mvn clean, npm run clean, cargo clean, rm -rf build/

# Install/update dependencies
[DEPENDENCY_INSTALL_COMMAND]
# Examples: mvn install, npm install, pip install -r requirements.txt, cargo fetch

# Initial build verification
[BUILD_COMMAND]
# Examples: mvn compile, npm run build, cargo check, go build ./...

# Run quick test to verify setup
[QUICK_TEST_COMMAND]
# Examples: mvn test -Dtest=QuickTest, npm test -- --quick, cargo test --lib
```

### Database/Services Setup (IF APPLICABLE)
```bash
# Start required services
[DATABASE_START_COMMAND]
# Examples:
# docker-compose up -d postgres
# brew services start postgresql
# systemctl start redis

# Verify database connection
[DATABASE_CONNECTION_TEST]
# Examples:
# psql -h localhost -p 5432 -U user -d database -c "SELECT 1;"
# redis-cli ping
# mongo --eval "db.runCommand('ping')"

# Run database migrations if needed
[MIGRATION_COMMAND]
# Examples:
# mvn flyway:migrate
# npm run db:migrate
# python manage.py migrate
```

### Development Server/Tools (IF APPLICABLE)
```bash
# Start development server
[DEV_SERVER_START_COMMAND]
# Examples:
# npm run dev
# mvn spring-boot:run
# python manage.py runserver
# cargo run

# Development tools startup
[DEV_TOOLS_COMMAND]
# Examples:
# webpack --mode development --watch
# sass --watch src/styles:dist/css
# tsc --watch
```

### Configuration Files Location
- **Main Config:** `[MAIN_CONFIG_FILE_PATH]`
- **Environment Config:** `[ENV_CONFIG_FILE_PATH]`
- **Database Config:** `[DATABASE_CONFIG_FILE_PATH]`
- **Build Config:** `[BUILD_CONFIG_FILE_PATH]`
- **IDE Config:** `[IDE_CONFIG_FILES]`

### Common Environment Issues & Solutions

#### Issue: "Command not found"
```bash
# Verify PATH includes necessary directories
echo $PATH
# Add missing paths (examples):
# export PATH=$PATH:/usr/local/bin
# export PATH=$PATH:~/.cargo/bin
# export PATH=$PATH:~/go/bin
```

#### Issue: "Permission denied"
```bash
# Fix common permission issues
chmod +x [SCRIPT_NAME]
sudo chown -R $USER:$GROUP [DIRECTORY_PATH]
```

#### Issue: "Port already in use"
```bash
# Find and kill process using port
lsof -ti:[PORT_NUMBER] | xargs kill -9
# Examples: lsof -ti:8080 | xargs kill -9
```

#### Issue: "Module/package not found"
```bash
# Reinstall dependencies
[DEPENDENCY_REINSTALL_COMMAND]
# Examples:
# rm -rf node_modules && npm install
# pip uninstall -r requirements.txt && pip install -r requirements.txt
# cargo clean && cargo fetch
```

### Environment Validation Checklist
- [ ] Runtime version matches requirements
- [ ] Build tool available and correct version
- [ ] All critical dependencies installed
- [ ] Configuration files present and valid
- [ ] Database/services accessible (if applicable)
- [ ] Development server starts successfully (if applicable)
- [ ] Quick test suite passes
- [ ] No permission or path issues

### Performance Optimization
```bash
# Increase build performance (examples)
[PERFORMANCE_OPTIMIZATION_COMMANDS]
# Examples:
# export MAVEN_OPTS="-Xmx2048m -XX:MaxPermSize=256m"
# npm config set registry https://registry.npmmirror.com/
# export CARGO_BUILD_JOBS=4
```

### Troubleshooting Commands
```bash
# Debug environment issues
[DEBUG_COMMANDS]
# Examples:
# mvn help:effective-settings
# npm doctor
# python -m pip check
# cargo check --verbose

# Reset environment completely (nuclear option)
[ENVIRONMENT_RESET_COMMANDS]
# Examples:
# deactivate && rm -rf .venv && python -m venv .venv && source .venv/bin/activate
# rm -rf node_modules package-lock.json && npm install
# cargo clean && rm Cargo.lock && cargo fetch
```

---

## 🎯 Session Start Verification

**Before starting any development work, verify:**
- [ ] Environment activated and verified
- [ ] All dependencies installed and up-to-date
- [ ] Build system working
- [ ] Database/services accessible (if applicable)
- [ ] Configuration files valid
- [ ] Quick test passes

**Total setup time:** ~3-5 minutes for configured environment, ~15-20 minutes for fresh setup

---

**IMPORTANT:** Update this document immediately when environment requirements change. Outdated environment procedures waste significant session time.