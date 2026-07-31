<#
.SYNOPSIS
Builds the frontend, packages the backend, and runs the application.

.DESCRIPTION
Defaults reproduce the demo exactly, so `.\run-demo.ps1` with no arguments is
still the one command a reader needs. The options exist because the previous
version could only ever do that one thing: every run reinstalled locked frontend
dependencies and did a clean package, a two-minute wait to restart a server
whose Java had not changed.

Kept deliberately in step with run-demo.sh: same switches, same defaults, same
messages. Two launch scripts that drift apart are worse than one.

.PARAMETER Port
HTTP port. Default 7070.

.PARAMETER Scenario
Startup scenario JSON. Default data/scenarios/district1_typical_day.json.

.PARAMETER Map
Topology-only startup map. The backend ignores it unless no scenario is set,
and so does this script.

.PARAMETER SkipFrontend
Do not reinstall or rebuild the frontend. The JAR keeps whatever UI it was last
packaged with.

.PARAMETER SkipBuild
Do not run Maven either. Needs an existing JAR; use it to relaunch with a
different scenario or port.

.EXAMPLE
.\run-demo.ps1

.EXAMPLE
.\run-demo.ps1 -Port 8080 -Scenario data/scenarios/test/u-turn.json

.EXAMPLE
.\run-demo.ps1 -SkipBuild -Scenario data/scenarios/test/moto-filtering.json
#>
[CmdletBinding()]
param(
    [int]$Port = $(if ($env:PORT) { [int]$env:PORT } else { 7070 }),
    [string]$Scenario = $env:SCENARIO_FILE,
    [string]$Map = $env:MAP_FILE,
    [switch]$SkipFrontend,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Skipping the backend build implies skipping the frontend one, because the
# frontend is served from inside the JAR that is not being rebuilt.
if ($SkipBuild) { $SkipFrontend = $true }

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE."
    }
}

function Assert-Command {
    param([string]$Name, [string]$Hint)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name -- $Hint"
    }
}

Assert-Command "java" "install a JDK 17 or newer"
if (-not $SkipBuild) { Assert-Command "mvn" "install Apache Maven 3.9 or newer" }
if (-not $SkipFrontend) { Assert-Command "npm" "install Node.js and npm" }

# Checked here rather than left to the backend: a typo in a path should fail
# before a two-minute build, not after it.
if ($Scenario -and -not (Test-Path -LiteralPath $Scenario -PathType Leaf)) {
    throw "Scenario file not found: $Scenario"
}
if ($Map -and -not (Test-Path -LiteralPath $Map -PathType Leaf)) {
    throw "Map file not found: $Map"
}

if (-not $SkipFrontend) {
    Write-Host "==> Frontend dependencies"
    Invoke-CheckedNative "Frontend dependency installation" { npm --prefix frontend ci }
    Write-Host "==> Frontend build"
    Invoke-CheckedNative "Frontend production build" { npm --prefix frontend run build }
}

if (-not $SkipBuild) {
    Write-Host "==> Backend package"
    Invoke-CheckedNative "Backend packaging" { mvn -q -DskipTests clean package }
}

# Located rather than hardcoded: the file name carries the project version, so
# pinning it means a version bump silently breaks the launch script.
#
# Shortest name wins. Maven names the artifact to launch with no classifier, and
# every other jar in target/ -- shaded, sources, javadoc, original -- adds one,
# so it is always the shortest. Listing classifiers to exclude instead would
# need editing whenever the build grows another one.
$jar = Get-ChildItem -Path "target" -Filter "traffic-event-system-*.jar" -ErrorAction SilentlyContinue |
    Sort-Object { $_.Name.Length } |
    Select-Object -First 1
if (-not $jar) {
    throw "No application JAR in target/. Run without -SkipBuild first."
}

Write-Host "==> http://localhost:$Port  ($($jar.Name))"
if ($Scenario) {
    Write-Host "    scenario: $Scenario"
} elseif ($Map) {
    Write-Host "    map:      $Map"
}

$env:PORT = "$Port"
if ($Scenario) { $env:SCENARIO_FILE = $Scenario }
if ($Map) { $env:MAP_FILE = $Map }

Invoke-CheckedNative "Application launch" { java -jar $jar.FullName }
