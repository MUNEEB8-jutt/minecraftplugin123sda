param(
    [string]$ServerDir = 'D:\New folder (3)\server',
    [switch]$StopFirst
)

. "$PSScriptRoot\server-common.ps1"

if ($StopFirst) {
    Stop-LegendaryServer -ServerDir $ServerDir
}

Remove-LegendaryWorldFolders -ServerDir $ServerDir
