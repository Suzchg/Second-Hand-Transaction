Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "  SecondHand System - Stopping..." -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host ""

function Stop-Port {
    param([int]$port, [string]$label)
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Closing $label ($($proc.ProcessName) PID:$($proc.Id)) on port $port..." -ForegroundColor Yellow
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Write-Host "  Stopped." -ForegroundColor Green
        }
    } else {
        Write-Host "Port $port - not in use" -ForegroundColor Gray
    }
}

# Frontend (Vite dev server)
Stop-Port 5173 "Frontend (Vite)"

# Backend (Spring Boot)
Stop-Port 8088 "Backend (Spring Boot)"

# cpolar tunnel (if running)
$cpolar = Get-Process -Name "cpolar" -ErrorAction SilentlyContinue
if ($cpolar) {
    Write-Host "Closing cpolar (PID:$($cpolar.Id))..." -ForegroundColor Yellow
    Stop-Process -Name "cpolar" -Force -ErrorAction SilentlyContinue
    Write-Host "  Stopped." -ForegroundColor Green
} else {
    Write-Host "cpolar - not running" -ForegroundColor Gray
}

# MySQL (optional — comment out if you use MySQL for other projects)
$mysqlSvc = Get-Service -Name "MySQL*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($mysqlSvc -and $mysqlSvc.Status -eq "Running") {
    Write-Host "Stopping MySQL ($($mysqlSvc.Name))..." -ForegroundColor Yellow
    Stop-Service $mysqlSvc.Name -ErrorAction SilentlyContinue
    Write-Host "  MySQL stopped." -ForegroundColor Green
} else {
    Write-Host "MySQL - not running" -ForegroundColor Gray
}

Write-Host ""
Write-Host "All services stopped." -ForegroundColor Green
Write-Host ""
