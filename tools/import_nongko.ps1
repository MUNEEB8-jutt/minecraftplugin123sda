$ErrorActionPreference = 'Stop'

$assetRoot = Join-Path $PSScriptRoot "..\nongko's_Fantasy_Weapons_v1.17A\assets\minecraft"
$modelSrcRoot = Join-Path $assetRoot "models\item"
$texSrcRoot = Join-Path $assetRoot "textures\item"
$modelOutRoot = Join-Path $PSScriptRoot "..\src\main\resources\rp\models\item"
$texOutRoot = Join-Path $PSScriptRoot "..\src\main\resources\rp\textures\item"

$mapping = [ordered]@{
    emerald_dagger = "azure_dagger.json"
    soul_scythe = "azure_scythe.json"
    hell_bringer = "demons_blood_blade.json"
    stone_breaker = "iron_mace.json"
    thunder_glaive = "heavenly_partisan.json"
    warden_katana = "gloomsteel_katana.json"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Normalize-Token([string]$value) {
    $v = [string]$value
    if ($v -match '^[^:]+:(.+)$') {
        $v = $matches[1]
    }
    $v = $v -replace '^item/', ''
    $v = $v -replace '\.png$', ''
    $v = $v.Trim()
    return $v
}

foreach ($entry in $mapping.GetEnumerator()) {
    $weapon = [string]$entry.Key
    $sourceModel = [string]$entry.Value
    $modelPath = Join-Path $modelSrcRoot $sourceModel
    if (-not (Test-Path $modelPath)) {
        throw "Missing model: $modelPath"
    }

    $model = (Get-Content $modelPath -Raw) | ConvertFrom-Json
    $textureDir = Join-Path $texOutRoot $weapon
    New-Item -ItemType Directory -Path $textureDir -Force | Out-Null
    $copied = New-Object 'System.Collections.Generic.List[string]'

    foreach ($prop in $model.textures.PSObject.Properties) {
        $raw = [string]$prop.Value
        if ($raw.StartsWith("#")) {
            continue
        }
        $token = Normalize-Token $raw
        if ([string]::IsNullOrWhiteSpace($token)) {
            continue
        }
        $prop.Value = "legendary:item/$weapon/$token"
        $srcPng = Join-Path $texSrcRoot ($token + ".png")
        if (-not (Test-Path $srcPng)) {
            throw "Missing texture $srcPng for model $sourceModel"
        }

        $dstPng = Join-Path $textureDir ($token + ".png")
        Copy-Item $srcPng $dstPng -Force
        if (-not $copied.Contains(($token + ".png"))) {
            [void]$copied.Add($token + ".png")
        }

        $srcMeta = $srcPng + ".mcmeta"
        if (Test-Path $srcMeta) {
            $dstMeta = $dstPng + ".mcmeta"
            Copy-Item $srcMeta $dstMeta -Force
            if (-not $copied.Contains(($token + ".png.mcmeta"))) {
                [void]$copied.Add($token + ".png.mcmeta")
            }
        }
    }

    $modelJson = $model | ConvertTo-Json -Depth 100 -Compress
    [System.IO.File]::WriteAllText((Join-Path $modelOutRoot ($weapon + ".json")), $modelJson, $utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $textureDir "index.txt"), ([string]::Join("`n", $copied)) + "`n", $utf8NoBom)
}

Add-Type -AssemblyName System.Drawing
$emeraldDir = Join-Path $texOutRoot "emerald_dagger"
if (Test-Path $emeraldDir) {
    foreach ($png in Get-ChildItem $emeraldDir -Filter *.png -File) {
        $bmp = New-Object System.Drawing.Bitmap($png.FullName)
        for ($y = 0; $y -lt $bmp.Height; $y++) {
            for ($x = 0; $x -lt $bmp.Width; $x++) {
                $c = $bmp.GetPixel($x, $y)
                if ($c.A -eq 0) {
                    continue
                }
                $lum = ($c.R + $c.G + $c.B) / 3.0
                $nr = [Math]::Min(255, [Math]::Max(0, [int][Math]::Round($c.R * 0.22 + $lum * 0.15)))
                $ng = [Math]::Min(255, [Math]::Max(0, [int][Math]::Round($c.G * 0.78 + $lum * 0.58 + 14)))
                $nb = [Math]::Min(255, [Math]::Max(0, [int][Math]::Round($c.B * 0.26 + $lum * 0.24)))
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $nr, $ng, $nb))
            }
        }
        $tmp = $png.FullName + ".tmp.png"
        $bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        Move-Item -Force $tmp $png.FullName
    }
}
