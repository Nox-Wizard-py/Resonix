# Resonix Build Helper Script
# Run this script to build Resonix with proper Java configuration

# Set Java environment
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = $env:PATH + ";C:\Program Files\Java\jdk-21\bin"

# Navigate to project
Set-Location "c:\Users\admin\OneDrive\Documents\Nox Tune"

# Display Java version
Write-Host "Using Java:" -ForegroundColor Green
java -version

Write-Host "`nBuilding Resonix..." -ForegroundColor Cyan

# Build the app
.\gradlew assembleDebug

Write-Host "`nBuild complete! APK location:" -ForegroundColor Green
Write-Host "app\build\outputs\apk\universal\debug\app-universal-debug.apk" -ForegroundColor Yellow
