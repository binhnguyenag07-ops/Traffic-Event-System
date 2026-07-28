$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Step,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE."
    }
}

Invoke-CheckedNative "Frontend dependency installation" {
    npm --prefix frontend ci
}
Invoke-CheckedNative "Frontend production build" {
    npm --prefix frontend run build
}
Invoke-CheckedNative "Backend packaging" {
    mvn -q -DskipTests clean package
}
Invoke-CheckedNative "Application launch" {
    java -jar target/traffic-event-system-1.0-SNAPSHOT.jar
}
