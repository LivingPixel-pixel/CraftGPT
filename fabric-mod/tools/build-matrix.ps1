param(
    [string[]]$Targets = @(),
    [ValidateSet('baseline', 'current', 'both')][string]$FabricProfile = 'baseline'
)
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$definitions = Get-Content (Join-Path $projectDirectory 'versions.json') -Raw | ConvertFrom-Json
$available = @($definitions.PSObject.Properties.Name)
if ($Targets.Count -eq 0) { $Targets = $available }
foreach ($target in $Targets) {
    if ($target -notin $available) { throw "Unknown Minecraft target: $target. Choose $($available -join ', ')." }
}
$profiles = if ($FabricProfile -eq 'both') { @('baseline', 'current') } else { @($FabricProfile) }
$reportDirectory = Join-Path $projectDirectory 'build/matrix'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$results = @()
Push-Location $projectDirectory
try {
    foreach ($target in $Targets) {
        foreach ($profile in $profiles) {
            $log = Join-Path $reportDirectory "$target-$profile.log"
            Write-Host "Building Minecraft $target, Fabric $profile..."
            $tasks = if ($profile -eq 'baseline') { @('test', 'packageRelease') } else { @('test', 'build') }
            & .\gradlew.bat --no-daemon @tasks "-PmcTarget=$target" "-PfabricProfile=$profile" *> $log
            $passed = $LASTEXITCODE -eq 0
            $results += [pscustomobject]@{ minecraft = $target; fabric = $profile; passed = $passed; log = $log }
            $results | ConvertTo-Json | Set-Content (Join-Path $reportDirectory 'results.json')
            if ($passed) { Write-Host "PASS $target ($profile)" }
            else { Write-Host "FAIL $target ($profile)"; Get-Content $log -Tail 45 }
        }
    }
} finally { Pop-Location }
$results | Format-Table minecraft, fabric, passed
if (@($results | Where-Object { -not $_.passed }).Count -gt 0) { throw "Build matrix failed. See $reportDirectory" }
