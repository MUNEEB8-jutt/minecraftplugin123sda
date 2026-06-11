$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot "..\\src\\main\\resources\\rp\\textures\\item"
New-Item -ItemType Directory -Path $root -Force | Out-Null

function New-Random([int]$seed) {
    return [System.Random]::new($seed)
}

function Clamp8([int]$v) {
    if ($v -lt 0) { return 0 }
    if ($v -gt 255) { return 255 }
    return $v
}

function Mix-Color([System.Drawing.Color]$a, [System.Drawing.Color]$b, [double]$t) {
    $tt = [math]::Max(0.0, [math]::Min(1.0, $t))
    $r = [int]($a.R + ($b.R - $a.R) * $tt)
    $g = [int]($a.G + ($b.G - $a.G) * $tt)
    $bl = [int]($a.B + ($b.B - $a.B) * $tt)
    return [System.Drawing.Color]::FromArgb(255, (Clamp8 $r), (Clamp8 $g), (Clamp8 $bl))
}

function Paint-MaterialTexture {
    param(
        [string]$path,
        [int]$seed,
        [System.Drawing.Color]$baseA,
        [System.Drawing.Color]$baseB,
        [System.Drawing.Color]$accent
    )

    $size = 64
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $rand = New-Random $seed

    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            $u = $x / [double]($size - 1)
            $v = $y / [double]($size - 1)
            $diag = ($u * 0.55) + ($v * 0.45)
            $wave = 0.06 * [math]::Sin(($x + $seed) * 0.38) + 0.05 * [math]::Cos(($y - $seed) * 0.31)
            $grain = (($rand.NextDouble() - 0.5) * 0.14)
            $t = [math]::Max(0.0, [math]::Min(1.0, $diag + $wave + $grain))
            $c = Mix-Color $baseA $baseB $t
            $bmp.SetPixel($x, $y, $c)
        }
    }

    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $soft = [System.Drawing.Color]::FromArgb(180, $accent.R, $accent.G, $accent.B)
    $linePen = New-Object System.Drawing.Pen($soft, 1.3)
    for ($i = -16; $i -le 64; $i += 8) {
        $g.DrawLine($linePen, $i, 0, $i + 18, 64)
    }

    $edgePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(190, 255, 255, 255), 1.0)
    $g.DrawRectangle($edgePen, 0, 0, 63, 63)

    $sparkBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, $accent.R, $accent.G, $accent.B))
    for ($i = 0; $i -lt 140; $i++) {
        $x = $rand.Next(0, 64)
        $y = $rand.Next(0, 64)
        $w = if ($rand.NextDouble() -lt 0.2) { 2 } else { 1 }
        $h = if ($rand.NextDouble() -lt 0.2) { 2 } else { 1 }
        $g.FillRectangle($sparkBrush, $x, $y, $w, $h)
    }

    $runePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180, [int](($accent.R + 255) / 2), [int](($accent.G + 255) / 2), [int](($accent.B + 255) / 2)), 1.0)
    for ($i = 0; $i -lt 12; $i++) {
        $x = 4 + $i * 5
        $g.DrawLine($runePen, $x, 8, $x + 2, 12)
        $g.DrawLine($runePen, $x + 2, 12, $x, 16)
    }

    $linePen.Dispose()
    $edgePen.Dispose()
    $sparkBrush.Dispose()
    $runePen.Dispose()
    $g.Dispose()

    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$weapons = @(
    @{ id = 'emerald_dagger'; seed = 1101; a = [System.Drawing.Color]::FromArgb(15, 68, 38);  b = [System.Drawing.Color]::FromArgb(72, 210, 134); accent = [System.Drawing.Color]::FromArgb(118, 255, 174) },
    @{ id = 'silk_crossbow';  seed = 1102; a = [System.Drawing.Color]::FromArgb(56, 54, 62);  b = [System.Drawing.Color]::FromArgb(218, 214, 230); accent = [System.Drawing.Color]::FromArgb(245, 245, 255) },
    @{ id = 'blizzard_bow';   seed = 1103; a = [System.Drawing.Color]::FromArgb(30, 62, 108); b = [System.Drawing.Color]::FromArgb(140, 214, 255); accent = [System.Drawing.Color]::FromArgb(206, 244, 255) },
    @{ id = 'soul_scythe';    seed = 1104; a = [System.Drawing.Color]::FromArgb(36, 40, 92);  b = [System.Drawing.Color]::FromArgb(116, 164, 250); accent = [System.Drawing.Color]::FromArgb(188, 220, 255) },
    @{ id = 'hell_bringer';   seed = 1105; a = [System.Drawing.Color]::FromArgb(92, 26, 18);  b = [System.Drawing.Color]::FromArgb(248, 96, 54);  accent = [System.Drawing.Color]::FromArgb(255, 194, 118) },
    @{ id = 'stone_breaker';  seed = 1106; a = [System.Drawing.Color]::FromArgb(62, 60, 58);  b = [System.Drawing.Color]::FromArgb(176, 170, 162); accent = [System.Drawing.Color]::FromArgb(230, 224, 206) },
    @{ id = 'thunder_glaive'; seed = 1107; a = [System.Drawing.Color]::FromArgb(92, 66, 18);  b = [System.Drawing.Color]::FromArgb(255, 194, 58);  accent = [System.Drawing.Color]::FromArgb(255, 238, 150) },
    @{ id = 'warden_katana';  seed = 1108; a = [System.Drawing.Color]::FromArgb(18, 70, 74);  b = [System.Drawing.Color]::FromArgb(72, 212, 226); accent = [System.Drawing.Color]::FromArgb(176, 248, 255) }
)

foreach ($w in $weapons) {
    $path = Join-Path $root "$($w.id).png"
    Paint-MaterialTexture -path $path -seed $w.seed -baseA $w.a -baseB $w.b -accent $w.accent
}
