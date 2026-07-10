# Copia de seguridad de la base de datos de producción de Sofrito.
#
# Los datos de la BD son la EVIDENCIA de los usuarios (diario sellado,
# cuadrantes con historial): perderlos es perder aquello para lo que existe
# la app. Este script hace un pg_dump (formato custom, comprimido) del
# servicio `db` del compose de producción, verifica que el archivo es
# legible (pg_restore --list), lo trae al host, aplica retención y,
# opcionalmente, deja una segunda copia en otra ruta (OneDrive, disco
# externo... la copia FUERA de esta máquina es la que vale ante un robo,
# un ransomware o un disco muerto).
#
# Uso manual:    .\backup-db.ps1
# Programado:    ver docs/backups.md (tarea diaria de Windows)
# Restauración:  .\restaura-db.ps1 (ensaya SIEMPRE antes de necesitarlo)
param(
    # Carpeta local de backups (fuera del repo: un `git clean` no puede llevárselos).
    [string]$Destino = "$env:USERPROFILE\Documents\sofrito-backups",
    # Segunda copia (recomendado: una carpeta sincronizada con la nube o un
    # disco externo). Vacío = solo copia local.
    [string]$CopiaExterna = '',
    # Días que se conservan los backups locales. La externa no se purga nunca
    # desde aquí (esa retención se gestiona donde viva).
    [int]$DiasRetencion = 30
)

$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot 'docker-compose.prod.yml'
if (-not (Test-Path $compose)) { throw "No encuentro $compose" }

$sello = Get-Date -Format 'yyyyMMdd-HHmmss'
$fichero = "sofrito-$sello.dump"
$rutaLocal = Join-Path $Destino $fichero
if (-not (Test-Path $Destino)) { New-Item -ItemType Directory -Force $Destino | Out-Null }

# 1) Dump DENTRO del contenedor a fichero y luego docker cp: el pipe de
#    PowerShell 5.1 corrompe binarios (convierte el stream a texto UTF-16),
#    así que el dump nunca debe atravesar stdout del host.
Write-Output "[backup] pg_dump de 'sofrito' en el servicio db..."
docker compose -f $compose exec -T db pg_dump -U sofrito -Fc -f "/tmp/$fichero" sofrito
if ($LASTEXITCODE -ne 0) { throw "pg_dump ha fallado (exit $LASTEXITCODE). ¿Está levantado el compose de producción?" }

# 2) Integridad ANTES de darlo por bueno: si pg_restore no puede listar el
#    archivo, no es un backup, es un fichero con nombre de backup.
docker compose -f $compose exec -T db pg_restore --list "/tmp/$fichero" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "El dump no pasa pg_restore --list: NO se guarda un backup corrupto." }

# 3) Traerlo al host y limpiar el temporal del contenedor.
docker compose -f $compose cp "db:/tmp/$fichero" $rutaLocal
if ($LASTEXITCODE -ne 0) { throw "docker cp ha fallado (exit $LASTEXITCODE)" }
docker compose -f $compose exec -T db rm -f "/tmp/$fichero"

$tamano = [math]::Round((Get-Item $rutaLocal).Length / 1MB, 2)
Write-Output "[backup] OK: $rutaLocal ($tamano MB)"

# 4) Segunda copia (la de verdad, fuera de esta máquina).
if ($CopiaExterna -ne '') {
    if (-not (Test-Path $CopiaExterna)) { New-Item -ItemType Directory -Force $CopiaExterna | Out-Null }
    Copy-Item $rutaLocal (Join-Path $CopiaExterna $fichero)
    Write-Output "[backup] Copia externa: $(Join-Path $CopiaExterna $fichero)"
} else {
    Write-Output "[backup] AVISO: sin copia externa (-CopiaExterna). Un backup en el mismo disco que la BD no cubre robo/ransomware/disco muerto."
}

# 5) Retención local: fuera lo más viejo que $DiasRetencion.
$purgados = Get-ChildItem $Destino -Filter 'sofrito-*.dump' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$DiasRetencion) }
if ($purgados) {
    $purgados | Remove-Item -Force -Confirm:$false
    Write-Output "[backup] Retención: purgados $($purgados.Count) backups de hace más de $DiasRetencion días."
}

Write-Output "[backup] Hecho. Ensaya la restauración de vez en cuando: .\restaura-db.ps1 -Fichero $rutaLocal"
