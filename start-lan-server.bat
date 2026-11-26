@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if not exist "%~dp0data" mkdir "%~dp0data"

REM JWT secret is generated for this session only. Nothing secret is stored in the repo.
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "$b = New-Object byte[] 48; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)"`) do set "JWT_SECRET=%%i"

set "DB_URL=jdbc:h2:file:%~dp0data\tukangkilat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0;CACHE_SIZE=8192"
set "DB_USER="
set "DB_PASSWORD="
set "JWT_ISSUER=tukangkilat-local"
set "PORT=8080"
set "UPLOAD_DIR=%~dp0uploads"
set "SEED_DEMO_DATA=true"
set "START_DISPATCHER=true"
set "DISPATCHER_TICK_MS=15000"
set "DEMO_ADMIN_PASSWORD=admin12345"
set "DEMO_CONSUMER_PASSWORD=rina12345"
set "DEMO_TECH_PASSWORD=budi12345"
set "DEMO_TECH2_PASSWORD=sari12345"

echo.
echo === Tukang Kilat LAN server ===
echo.
echo IP LAN laptop (pakai salah satu yang bukan 127.0.0.1):
powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -ne '127.0.0.1' } | Select-Object -ExpandProperty IPAddress"
echo.
echo Di HP, buka aplikasi lalu isi alamat server:
echo     http://IP_LAPTOP:8080
echo Contoh: http://192.168.1.5:8080
echo.
echo Jika Windows Defender bertanya, pilih Allow / Izinkan.
echo Tekan Ctrl+C untuk menghentikan server.
echo.

call .\gradlew.bat :backend:run --console=plain
pause
