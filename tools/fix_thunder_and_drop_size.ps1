$ErrorActionPreference = "Stop"

$root = "d:/downloads/minecraft-weapons-plugin"
$rx = Join-Path $root "RxGlorySMP-S2Texture/assets/minecraft/optifine/cit"
$models = Join-Path $root "src/main/resources/rp/models/item"
$textures = Join-Path $root "src/main/resources/rp/textures/item"

function Write-Ascii {
    param(
        [string]$Path,
        [string]$Content
    )
    Set-Content -Path $Path -Value $Content -Encoding Ascii
}

function Ensure-Dir {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Rewrite-Model {
    param(
        [string]$SourceFile,
        [string]$TargetFile,
        [string]$WeaponId
    )
    $model = Get-Content -Raw $SourceFile
    $model = $model -replace "malikaset:\./", "legendary:item/$WeaponId/"
    $model = $model -replace "azureset:\./", "legendary:item/$WeaponId/"
    $model = $model -replace "slayer_set:\./", "legendary:item/$WeaponId/"
    $model = $model -replace "`"\./", "`"legendary:item/$WeaponId/"
    Write-Ascii $TargetFile $model
}

# 1) Thunder Glaive should not share Emerald Dagger look.
Rewrite-Model (Join-Path $rx "d/20.json") (Join-Path $models "thunder_glaive.json") "thunder_glaive"
Ensure-Dir (Join-Path $textures "thunder_glaive")
Copy-Item -Force (Join-Path $rx "d/sword.png") (Join-Path $textures "thunder_glaive/sword.png")
Copy-Item -Force (Join-Path $rx "d/animation_1.png") (Join-Path $textures "thunder_glaive/animation_1.png")
Copy-Item -Force (Join-Path $rx "d/animation_1.png.mcmeta") (Join-Path $textures "thunder_glaive/animation_1.png.mcmeta")
Write-Ascii (Join-Path $textures "thunder_glaive/index.txt") ((@(
    "sword.png",
    "animation_1.png",
    "animation_1.png.mcmeta"
) -join "`n") + "`n")

# 2) Uniform dropped size for all legendary items.
$ground = [ordered]@{
    translation = @(0, 11.5, 0)
    scale = @(1.8, 1.8, 1.8)
}

$modelFiles = @(
    "emerald_dagger.json",
    "silk_crossbow.json",
    "blizzard_bow.json",
    "soul_scythe.json",
    "hell_bringer.json",
    "stone_breaker.json",
    "thunder_glaive.json",
    "warden_katana.json"
)

foreach ($mf in $modelFiles) {
    $path = Join-Path $models $mf
    if (-not (Test-Path $path)) {
        continue
    }
    $obj = Get-Content -Raw $path | ConvertFrom-Json
    if ($null -eq $obj.display) {
        $obj | Add-Member -NotePropertyName display -NotePropertyValue ([ordered]@{}) -Force
    }
    if ($obj.display.PSObject.Properties.Name -contains "ground") {
        $obj.display.ground = $ground
    } else {
        $obj.display | Add-Member -NotePropertyName ground -NotePropertyValue $ground -Force
    }
    $json = $obj | ConvertTo-Json -Depth 100 -Compress
    Set-Content -Path $path -Value $json -Encoding Ascii
}

Write-Output "Thunder and drop-size normalization complete"
