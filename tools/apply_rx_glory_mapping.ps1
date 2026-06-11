$ErrorActionPreference = "Stop"

$rx = "d:/downloads/minecraft-weapons-plugin/RxGlorySMP-S2Texture/assets/minecraft/optifine/cit"
$models = "d:/downloads/minecraft-weapons-plugin/src/main/resources/rp/models/item"
$textures = "d:/downloads/minecraft-weapons-plugin/src/main/resources/rp/textures/item"

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

Rewrite-Model (Join-Path $rx "c/bow.json") (Join-Path $models "blizzard_bow.json") "blizzard_bow"
Rewrite-Model (Join-Path $rx "c/bow_0.json") (Join-Path $models "blizzard_bow_pulling_0.json") "blizzard_bow"
Rewrite-Model (Join-Path $rx "c/bow_1.json") (Join-Path $models "blizzard_bow_pulling_1.json") "blizzard_bow"
Rewrite-Model (Join-Path $rx "c/bow_2.json") (Join-Path $models "blizzard_bow_pulling_2.json") "blizzard_bow"
Copy-List (Join-Path $rx "c") (Join-Path $textures "blizzard_bow") @(
    "b.png",
    "b1.png",
    "b2.png",
    "animations_1.png",
    "animations_1.png.mcmeta",
    "animations_2.png",
    "animations_2.png.mcmeta",
    "animations_3.png",
    "animations_3.png.mcmeta"
)
Write-Ascii (Join-Path $textures "blizzard_bow/index.txt") (@(
    "b.png",
    "b1.png",
    "b2.png",
    "animations_1.png",
    "animations_1.png.mcmeta",
    "animations_2.png",
    "animations_2.png.mcmeta",
    "animations_3.png",
    "animations_3.png.mcmeta"
) -join "`n")

Write-Ascii (Join-Path $models "silk_crossbow.json") '{"parent":"minecraft:item/crossbow","textures":{"layer0":"legendary:item/silk_crossbow/crossbow_standby"}}'
Write-Ascii (Join-Path $models "silk_crossbow_pulling_0.json") '{"parent":"minecraft:item/crossbow_pulling_0","textures":{"layer0":"legendary:item/silk_crossbow/crossbow_pulling_0"}}'
Write-Ascii (Join-Path $models "silk_crossbow_pulling_1.json") '{"parent":"minecraft:item/crossbow_pulling_1","textures":{"layer0":"legendary:item/silk_crossbow/crossbow_pulling_1"}}'
Write-Ascii (Join-Path $models "silk_crossbow_pulling_2.json") '{"parent":"minecraft:item/crossbow_pulling_2","textures":{"layer0":"legendary:item/silk_crossbow/crossbow_pulling_2"}}'
Write-Ascii (Join-Path $models "silk_crossbow_charged.json") '{"parent":"minecraft:item/crossbow","textures":{"layer0":"legendary:item/silk_crossbow/crossbow_arrow","layer1":"minecraft:item/arrow"}}'
Write-Ascii (Join-Path $models "silk_crossbow_firework.json") '{"parent":"minecraft:item/crossbow","textures":{"layer0":"legendary:item/silk_crossbow/crossbow_firework","layer1":"minecraft:item/firework_rocket"}}'
Copy-List (Join-Path $rx "crossbow") (Join-Path $textures "silk_crossbow") @(
    "crossbow_standby.png",
    "crossbow_pulling_0.png",
    "crossbow_pulling_1.png",
    "crossbow_pulling_2.png",
    "crossbow_arrow.png",
    "crossbow_firework.png"
)
Write-Ascii (Join-Path $textures "silk_crossbow/index.txt") (@(
    "crossbow_standby.png",
    "crossbow_pulling_0.png",
    "crossbow_pulling_1.png",
    "crossbow_pulling_2.png",
    "crossbow_arrow.png",
    "crossbow_firework.png"
) -join "`n")

Rewrite-Model (Join-Path $rx "a/greatsword.json") (Join-Path $models "emerald_dagger.json") "emerald_dagger"
Copy-List (Join-Path $rx "a") (Join-Path $textures "emerald_dagger") @(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
)
Write-Ascii (Join-Path $textures "emerald_dagger/index.txt") (@(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
) -join "`n")

Rewrite-Model (Join-Path $rx "b/sword.json") (Join-Path $models "soul_scythe.json") "soul_scythe"
Copy-List (Join-Path $rx "b") (Join-Path $textures "soul_scythe") @(
    "c.png",
    "animations_1_e.png",
    "animations_1_e.png.mcmeta",
    "animations_2_e.png",
    "animations_2_e.png.mcmeta"
)
Write-Ascii (Join-Path $textures "soul_scythe/index.txt") (@(
    "c.png",
    "animations_1_e.png",
    "animations_1_e.png.mcmeta",
    "animations_2_e.png",
    "animations_2_e.png.mcmeta"
) -join "`n")

Rewrite-Model (Join-Path $rx "d/20.json") (Join-Path $models "hell_bringer.json") "hell_bringer"
Copy-List (Join-Path $rx "d") (Join-Path $textures "hell_bringer") @(
    "sword.png",
    "animation_1.png",
    "animation_1.png.mcmeta"
)
Write-Ascii (Join-Path $textures "hell_bringer/index.txt") (@(
    "sword.png",
    "animation_1.png",
    "animation_1.png.mcmeta"
) -join "`n")

Rewrite-Model (Join-Path $rx "god/thor_hammer_shiny.json") (Join-Path $models "stone_breaker.json") "stone_breaker"
Copy-List (Join-Path $rx "god") (Join-Path $textures "stone_breaker") @(
    "rr.png",
    "spark_fx_a.png",
    "spark_fx_a.png.mcmeta",
    "2_spark_fx_a.png",
    "2_spark_fx_a.png.mcmeta"
)
Write-Ascii (Join-Path $textures "stone_breaker/index.txt") (@(
    "rr.png",
    "spark_fx_a.png",
    "spark_fx_a.png.mcmeta",
    "2_spark_fx_a.png",
    "2_spark_fx_a.png.mcmeta"
) -join "`n")

Rewrite-Model (Join-Path $rx "a/greatsword.json") (Join-Path $models "thunder_glaive.json") "thunder_glaive"
Copy-List (Join-Path $rx "a") (Join-Path $textures "thunder_glaive") @(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
)
Write-Ascii (Join-Path $textures "thunder_glaive/index.txt") (@(
    "f.png",
    "animations_1.png",
    "animations_1.png.mcmeta"
) -join "`n")

Rewrite-Model (Join-Path $rx "b/sword.json") (Join-Path $models "warden_katana.json") "warden_katana"
Copy-List (Join-Path $rx "b") (Join-Path $textures "warden_katana") @(
    "c.png",
    "animations_1_e.png",
    "animations_1_e.png.mcmeta",
    "animations_2_e.png",
    "animations_2_e.png.mcmeta"
)
Write-Ascii (Join-Path $textures "warden_katana/index.txt") (@(
    "c.png",
    "animations_1_e.png",
    "animations_1_e.png.mcmeta",
    "animations_2_e.png",
    "animations_2_e.png.mcmeta"
) -join "`n")

Write-Output "RX mapping complete"
