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

function Copy-List {
    param(
        [string]$SourceDir,
        [string]$TargetDir,
        [string[]]$Names
    )
    Ensure-Dir $TargetDir
    foreach ($n in $Names) {
        Copy-Item -Force (Join-Path $SourceDir $n) (Join-Path $TargetDir $n)
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

# Requested remap targets only:
# emerald_dagger, hell_bringer, thunder_glaive, warden_katana, stone_breaker
# Excluded by user (not touched): soul_scythe, blizzard_bow, silk_crossbow

Rewrite-Model (Join-Path $rx "a/greatsword.json") (Join-Path $models "emerald_dagger.json") "emerald_dagger"
Copy-List (Join-Path $rx "a") (Join-Path $textures "emerald_dagger") @(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
)
Write-Ascii (Join-Path $textures "emerald_dagger/index.txt") ((@(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
) -join "`n") + "`n")

Rewrite-Model (Join-Path $rx "d/20.json") (Join-Path $models "hell_bringer.json") "hell_bringer"
Copy-List (Join-Path $rx "d") (Join-Path $textures "hell_bringer") @(
    "sword.png",
    "animation_1.png",
    "animation_1.png.mcmeta"
)
Write-Ascii (Join-Path $textures "hell_bringer/index.txt") ((@(
    "sword.png",
    "animation_1.png",
    "animation_1.png.mcmeta"
) -join "`n") + "`n")

Rewrite-Model (Join-Path $rx "god/thor_hammer_shiny.json") (Join-Path $models "stone_breaker.json") "stone_breaker"
Copy-List (Join-Path $rx "god") (Join-Path $textures "stone_breaker") @(
    "rr.png",
    "spark_fx_a.png",
    "spark_fx_a.png.mcmeta",
    "2_spark_fx_a.png",
    "2_spark_fx_a.png.mcmeta"
)
Write-Ascii (Join-Path $textures "stone_breaker/index.txt") ((@(
    "rr.png",
    "spark_fx_a.png",
    "spark_fx_a.png.mcmeta",
    "2_spark_fx_a.png",
    "2_spark_fx_a.png.mcmeta"
) -join "`n") + "`n")

Rewrite-Model (Join-Path $rx "a/greatsword.json") (Join-Path $models "thunder_glaive.json") "thunder_glaive"
Copy-List (Join-Path $rx "a") (Join-Path $textures "thunder_glaive") @(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
)
Write-Ascii (Join-Path $textures "thunder_glaive/index.txt") ((@(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
) -join "`n") + "`n")

Rewrite-Model (Join-Path $rx "b/sword.json") (Join-Path $models "warden_katana.json") "warden_katana"
Copy-List (Join-Path $rx "b") (Join-Path $textures "warden_katana") @(
    "c.png",
    "animations_1_e.png",
    "animations_1_e.png.mcmeta",
    "animations_2_e.png",
    "animations_2_e.png.mcmeta"
)
Write-Ascii (Join-Path $textures "warden_katana/index.txt") ((@(
    "c.png",
    "animations_1_e.png",
    "animations_1_e.png.mcmeta",
    "animations_2_e.png",
    "animations_2_e.png.mcmeta"
) -join "`n") + "`n")

Write-Output "RX selected mapping complete"
