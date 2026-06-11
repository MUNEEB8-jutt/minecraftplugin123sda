param()

$DefaultLegendaryServerDir = 'D:\New folder (3)\server'

function Resolve-LegendaryServerDir {
    param(
        [string]$ServerDir = $DefaultLegendaryServerDir
    )

    if (-not (Test-Path -LiteralPath $ServerDir)) {
        throw "Server directory not found: $ServerDir"
    }
    return (Resolve-Path -LiteralPath $ServerDir).Path.TrimEnd('\')
}

function Get-LegendaryServerProcess {
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match 'java' -and
        $_.CommandLine -and
        $_.CommandLine.Contains('paper.jar')
    }
}

function Stop-LegendaryServer {
    param(
        [string]$ServerDir = $DefaultLegendaryServerDir
    )

    [void](Resolve-LegendaryServerDir -ServerDir $ServerDir)
    $procs = @(Get-LegendaryServerProcess)
    if ($procs.Count -eq 0) {
        Write-Host 'Server is not running.'
        return
    }

    foreach ($proc in $procs) {
        Write-Host "Stopping server process $($proc.ProcessId)..."
        Stop-Process -Id $proc.ProcessId -Force
    }
    Start-Sleep -Seconds 2
}

function Remove-LegendaryWorldFolders {
    param(
        [string]$ServerDir = $DefaultLegendaryServerDir
    )

    $serverRoot = Resolve-LegendaryServerDir -ServerDir $ServerDir
    $running = @(Get-LegendaryServerProcess)
    if ($running.Count -gt 0) {
        throw 'Server is still running; refusing to delete world folders.'
    }

    foreach ($name in @('world', 'world_nether', 'world_the_end')) {
        $target = Join-Path $serverRoot $name
        if (-not (Test-Path -LiteralPath $target)) {
            Write-Host "Skipping missing world folder: $target"
            continue
        }

        $resolved = (Resolve-Path -LiteralPath $target).Path
        if (-not $resolved.StartsWith($serverRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to delete outside server dir: $resolved"
        }

        Write-Host "Deleting world folder: $resolved"
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

function Start-LegendaryServer {
    param(
        [string]$ServerDir = $DefaultLegendaryServerDir
    )

    $serverRoot = Resolve-LegendaryServerDir -ServerDir $ServerDir
    $running = @(Get-LegendaryServerProcess)
    if ($running.Count -gt 0) {
        Write-Host "Server already running. PID(s): $($running.ProcessId -join ', ')"
        return
    }

    $out = Join-Path $serverRoot 'codex-server-stdout.log'
    $err = Join-Path $serverRoot 'codex-server-stderr.log'
    Set-Content -LiteralPath $out -Value ''
    Set-Content -LiteralPath $err -Value ''

    $proc = Start-Process `
        -FilePath 'java' `
        -ArgumentList @(
            '-Xms1G',
            '-Xmx1536M',
            '-XX:+UseG1GC',
            '-XX:+ParallelRefProcEnabled',
            '-XX:MaxGCPauseMillis=200',
            '-jar',
            'paper.jar',
            'nogui'
        ) `
        -WorkingDirectory $serverRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput $out `
        -RedirectStandardError $err `
        -PassThru

    Write-Host "Server started. PID: $($proc.Id)"
}
