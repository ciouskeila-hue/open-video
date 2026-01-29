#!/bin/bash
set -e

PROJECT_ROOT="src"

# Navigate to project root
if [ -d "$PROJECT_ROOT" ]; then
    cd "$PROJECT_ROOT"
else
    echo "Error: $PROJECT_ROOT directory not found."
    exit 1
fi

# 1. Restructure if needed
# The source zip had a nested src/src structure. We normalize it to src/app/src.
if [ ! -d "app" ]; then
    echo "Restructuring project files..."
    mkdir -p app

    # Move source code
    if [ -d "src" ]; then
        mv src app/
    fi

    # Move app-level build file
    if [ -f "build.gradle.kts" ]; then
        if grep -q "implementation" "build.gradle.kts"; then
             mv build.gradle.kts app/
        fi
    fi

    # Move proguard rules
    if [ -f "proguard-rules.pro" ]; then
        mv proguard-rules.pro app/
    fi
else
    echo "Project structure seems to have 'app' directory."
    # Double check if src exists in root, which would be wrong
    if [ -d "src" ]; then
        echo "Found 'src' in root, moving to app/"
        mv src app/
    fi
fi

# 2. Create root build.gradle.kts if missing
if [ ! -f "build.gradle.kts" ]; then
    echo "Creating root build.gradle.kts..."
    cat > build.gradle.kts <<EOF
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
}
EOF
fi

# 3. Fix missing test directory which causes build failure
mkdir -p app/src/test/kotlin

# 4. Generate Gradle Wrapper if missing or broken
if [ ! -f "gradlew" ]; then
    echo "Generating Gradle Wrapper..."
    gradle wrapper

    # Remove checksum to avoid verification errors
    if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
        sed -i '/distributionSha256Sum/d' gradle/wrapper/gradle-wrapper.properties
    fi
fi

# 5. Build and Test
echo "Building APK and Running Tests..."
if [ -f "gradlew" ]; then
    chmod +x gradlew

    # Run tests first
    ./gradlew testDebugUnitTest

    # Then build APK
    ./gradlew assembleDebug

    echo "Build complete. APK should be in app/build/outputs/apk/debug/"

    # Attempt install (optional, may fail if no device)
    echo "Attempting to install (if device connected)..."
    set +e
    ./gradlew installDebug
    set -e
else
    echo "Error: gradlew not found."
    exit 1
fi
