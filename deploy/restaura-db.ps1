# Restauración de un backup de Starter.
#
# Un backup solo existe si se ha restaurado alguna vez. Este script tiene dos
# modos:
#
#   ENSAYO (por defecto, inofensivo): levanta un Postgres EFÍMERO aparte,
#   restaura el dump ahí, enseña el recuento de filas por tabla y lo destruye.
#   No toca producción. Hazlo de vez en cuando; si el ensayo no pasa, el
#   backup no vale.
#
#   PRODUCCIÓN (-SobreLaBaseDeProduccion, DESTRUCTIVO): machaca la BD del
#   compose de producción con el contenido del dump. Solo para recuperación
#   real de desastre. Pide confirmación escrita y conviene parar el backend
#   antes (docker compose -f deploy/docker-compose.prod.yml stop backend).
param(
    [Parameter(Mandatory = $true)][string]$Fichero,
    [switch]$SobreLaBaseDeProduccion
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Fichero)) { throw "No existe el fichero $Fichero" }
$nombre = Split-Path $Fichero -Leaf

if ($SobreLaBaseDeProduccion) {
    $compose = Join-Path $PSScriptRoot 'docker-compose.prod.yml'
    Write-Output "VAS A MACHACAR la base de datos de PRODUCCIÓN con: $nombre"
    Write-Output "Los datos actuales se pierden. Para el backend antes si no lo has hecho."
    $confirma = Read-Host "Escribe RESTAURAR para seguir"
    if ($confirma -cne 'RESTAURAR') { throw 'Cancelado.' }

    docker compose -f $compose cp $Fichero "db:/tmp/$nombre"
    if ($LASTEXITCODE -ne 0) { throw 'docker cp ha fallado' }
    # --clean --if-exists: deja la BD como en el dump, sin residuos de después.
    docker compose -f $compose exec -T db pg_restore -U app --clean --if-exists -d app "/tmp/$nombre"
    if ($LASTEXITCODE -ne 0) { throw "pg_restore ha fallado (exit $LASTEXITCODE)" }
    docker compose -f $compose exec -T db rm -f "/tmp/$nombre"
    Write-Output '[restaura] Producción restaurada. Arranca el backend y comprueba /api/v1/health y un login.'
    return
}

# --- ENSAYO en contenedor efímero ---
$drill = 'app-restore-drill'
Write-Output "[ensayo] Levantando Postgres efímero '$drill'..."
# Sin redirigir stderr: en PowerShell 5.1, `2>$null` sobre un exe convierte
# cada línea de stderr en error terminante con ErrorActionPreference=Stop.
$restos = docker ps -aq --filter "name=^$drill$"
if ($restos) { docker rm -f $drill | Out-Null }
docker run -d --name $drill -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=app postgres:16 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudo levantar el contenedor de ensayo' }

try {
    # Esperar a que acepte conexiones.
    $listo = $false
    foreach ($i in 1..30) {
        docker exec $drill pg_isready -U postgres | Out-Null
        if ($LASTEXITCODE -eq 0) { $listo = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $listo) { throw 'El Postgres de ensayo no arranca' }

    docker cp $Fichero "${drill}:/tmp/$nombre"
    if ($LASTEXITCODE -ne 0) { throw 'docker cp al ensayo ha fallado' }

    Write-Output "[ensayo] Restaurando $nombre..."
    docker exec $drill pg_restore -U postgres --no-owner -d app "/tmp/$nombre"
    if ($LASTEXITCODE -ne 0) { throw "pg_restore ha fallado en el ensayo (exit $LASTEXITCODE): este backup NO sirve" }

    Write-Output '[ensayo] Filas restauradas por tabla:'
    docker exec $drill psql -U postgres -d app -t -A -F ': ' -c "SELECT 'usuarios' AS tabla, count(*) FROM usuarios UNION ALL SELECT 'perfiles', count(*) FROM perfiles UNION ALL SELECT 'cuadrantes', count(*) FROM cuadrantes UNION ALL SELECT 'apuntes', count(*) FROM apuntes;"
    if ($LASTEXITCODE -ne 0) { throw 'El recuento de tablas ha fallado: faltan tablas en el dump' }

    Write-Output "[ensayo] OK: el backup $nombre restaura y las 4 tablas están."
} finally {
    docker rm -f $drill | Out-Null
    Write-Output '[ensayo] Contenedor efímero eliminado.'
}
