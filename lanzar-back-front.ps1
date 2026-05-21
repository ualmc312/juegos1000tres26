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

$googleAiStudioApiKey = 'AIzaSyAt4J_4xLbdKN8mr-AuIfY_ZdstIm4iiYk'

# Terminal 1: backend + base de datos (Docker Compose)
$escapedApiKey = $googleAiStudioApiKey.Replace("'", "''")
$backendCommand = "Set-Location '$projectRoot'; `$env:GOOGLE_AI_STUDIO_API_KEY = '$escapedApiKey'; docker compose up"

Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-Command',
    $backendCommand
)

# Terminal 2: frontend (Angular)
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-Command',
    "Set-Location '$frontendPath'; npm start"
)

Write-Host 'Se lanzaron backend y frontend en dos terminales separadas.'
