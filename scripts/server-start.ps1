param(
    [string]$ServerDir = 'D:\New folder (3)\server'
)

. "$PSScriptRoot\server-common.ps1"

Start-LegendaryServer -ServerDir $ServerDir
