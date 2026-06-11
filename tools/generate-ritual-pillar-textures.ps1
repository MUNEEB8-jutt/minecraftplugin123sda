param(
    [string]$Source = "src/main/resources/rp/assets/minecraft/textures/block/altar.png",
    [string]$OutputDir = "src/main/resources/rp/assets/legendary/textures/item/ritual"
)

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"
$root = (Resolve-Path ".").Path
$sourcePath = Join-Path $root $Source
$outputPath = Join-Path $root $OutputDir
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$colors = [ordered]@{
    "quantum_chronoblade" = @(96, 56, 170)
    "drakefire_katana" = @(235, 68, 28)
    "goldfang_dagger" = @(245, 185, 52)
    "bloodchain_ripper" = @(185, 18, 30)
    "frostnova_chakram" = @(84, 205, 255)
    "petalstorm_fanblade" = @(245, 138, 205)
    "stormbreaker_relic" = @(255, 220, 62)
    "sanguine_pike" = @(155, 10, 38)
    "voidglass_lich_staff" = @(58, 220, 130)
    "bifrost_wand" = @(255, 215, 72)
    "necromancer_reaper" = @(126, 82, 230)
    "timberlord_axe" = @(100, 175, 72)
    "bloomshot_blaster" = @(226, 116, 206)
    "hornhook_harpoon" = @(44, 178, 188)
    "tempest_sonicbow" = @(70, 220, 255)
}

function Clamp-Byte([double]$value) {
    return [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($value)))
}

foreach ($entry in $colors.GetEnumerator()) {
    $img = [System.Drawing.Bitmap]::new($sourcePath)
    $target = $entry.Value
    for ($y = 0; $y -lt $img.Height; $y++) {
        for ($x = 0; $x -lt $img.Width; $x++) {
            $pixel = $img.GetPixel($x, $y)
            if ($pixel.A -eq 0) {
                continue
            }

            $lum = (($pixel.R * 0.299) + ($pixel.G * 0.587) + ($pixel.B * 0.114)) / 255.0
            $shade = 0.28 + ([Math]::Pow($lum, 0.84) * 0.92)
            $edge = if ($lum -lt 0.18) { 0.45 } elseif ($lum -gt 0.78) { 0.24 } else { 0.34 }

            $r = Clamp-Byte (($target[0] * $shade * 0.78) + ($pixel.R * $edge))
            $g = Clamp-Byte (($target[1] * $shade * 0.78) + ($pixel.G * $edge))
            $b = Clamp-Byte (($target[2] * $shade * 0.78) + ($pixel.B * $edge))

            $img.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $r, $g, $b))
        }
    }
    $dest = Join-Path $outputPath ("altar_{0}.png" -f $entry.Key)
    $img.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose()
}

$manifestPath = Join-Path $root "src/main/resources/rp/assets_manifest.txt"
$manifest = New-Object System.Collections.Generic.List[string]
if (Test-Path $manifestPath) {
    [string[]]$existing = Get-Content -LiteralPath $manifestPath
    $manifest.AddRange($existing)
}
foreach ($id in $colors.Keys) {
    $line = "rp/assets/legendary/textures/item/ritual/altar_$id.png"
    if (-not $manifest.Contains($line)) {
        $manifest.Add($line)
    }
}
Set-Content -LiteralPath $manifestPath -Value $manifest -Encoding UTF8
