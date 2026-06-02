Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "  SecondHand System - Starting..." -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host ""

# Set JDK 24 as JAVA_HOME (required for Spring Boot 3)
$env:JAVA_HOME = "D:\JAVA\IDK24"
$env:PATH = "${env:JAVA_HOME}\bin;$env:PATH"

Write-Host "JAVA_HOME set to: $env:JAVA_HOME" -ForegroundColor Cyan
Write-Host ""

Write-Host "Starting backend (port 8088)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\backend'; .\mvnw.cmd -DskipTests spring-boot:run"

Write-Host "Starting frontend (port 5173)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\frontend'; npm run dev"

Write-Host ""
Write-Host "Then open in browser:" -ForegroundColor Yellow
Write-Host "  Frontend:  http://localhost:5173" -ForegroundColor Green
Write-Host "  Backend:   http://localhost:8088" -ForegroundColor Green
Write-Host "  Swagger:   http://localhost:8088/swagger" -ForegroundColor Green
Write-Host "  H2 Console: http://localhost:8088/h2-console" -ForegroundColor Green
Write-Host ""
