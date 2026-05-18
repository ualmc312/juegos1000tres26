$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendPath = Join-Path $projectRoot 'juegos1000tres-frontend'
$composePath = Join-Path $projectRoot 'docker-compose.yml'

if (-not (Test-Path $frontendPath)) {
    Write-Error "No se encontro la carpeta del frontend: $frontendPath"
}

if (-not (Test-Path $composePath)) {
    Write-Error "No se encontro el archivo docker-compose.yml en la raiz: $composePath"
}

# Terminal 1: backend + base de datos (Docker Compose)
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-Command',
    "Set-Location '$projectRoot'; docker compose up"
)

# Terminal 2: frontend (Angular)
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-Command',
    "Set-Location '$frontendPath'; npm start"
)

Write-Host 'Se lanzaron backend y frontend en dos terminales separadas.'
