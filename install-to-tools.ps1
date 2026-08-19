# Builds the current Maven project and deploys src/target into the local
# athena tools directory, replacing whatever was there before.

$ErrorActionPreference = 'Stop'

$destRoot = Join-Path $env:USERPROFILE '.agena.ai\tools\api'

Write-Host "Running mvn clean install in $(Get-Location)..."
# -D args are quoted individually — mvn.cmd's Windows batch parsing can mangle
# unquoted -D flags that contain dots (drops the leading segment).
mvn clean install "-DskipTests" "-Dmaven.javadoc.skip=true"
if ($LASTEXITCODE -ne 0) {
    Write-Error "mvn clean install failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Removing existing src/target in $destRoot..."
foreach ($dir in @('src', 'target')) {
    $path = Join-Path $destRoot $dir
    if (Test-Path $path) {
        Remove-Item -Path $path -Recurse -Force
    }
}

Write-Host "Copying src/target to $destRoot..."
New-Item -ItemType Directory -Force -Path $destRoot | Out-Null
foreach ($dir in @('src', 'target')) {
    Copy-Item -Path ".\$dir" -Destination (Join-Path $destRoot $dir) -Recurse -Force
}

Write-Host "Done."
