param(
    [string]$AssetRoot = "src/main/resources/rp/assets/minecraft"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$root = Join-Path (Get-Location) $AssetRoot
$textureDir = Join-Path $root "textures/item"
$modelDir = Join-Path $root "models/custom"
New-Item -ItemType Directory -Force -Path $textureDir | Out-Null
New-Item -ItemType Directory -Force -Path $modelDir | Out-Null

function New-Canvas {
    $bitmap = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.Clear([System.Drawing.Color]::Transparent)
    return @{ Bitmap = $bitmap; Graphics = $graphics }
}

function Save-Png($canvas, [string]$path) {
    $canvas.Graphics.Dispose()
    $canvas.Bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $canvas.Bitmap.Dispose()
}

function Add-PolygonPath($path, [double[][]]$points, [double]$cx, [double]$cy, [double]$sx, [double]$sy) {
    $first = $true
    foreach ($point in $points) {
        $x = [single]($cx + $point[0] * $sx)
        $y = [single]($cy + $point[1] * $sy)
        if ($first) {
            $path.StartFigure()
            $first = $false
        } else {
            $path.AddLine($lastX, $lastY, $x, $y)
        }
        $lastX = $x
        $lastY = $y
    }
    $path.CloseFigure()
}

function New-BatSigil {
    $canvas = New-Canvas
    $g = $canvas.Graphics
    $black = [System.Drawing.Color]::FromArgb(255, 4, 5, 6)
    $yellow = [System.Drawing.Color]::FromArgb(255, 247, 232, 38)
    $g.FillEllipse([System.Drawing.SolidBrush]::new($yellow), 16, 48, 224, 142)
    $g.DrawEllipse([System.Drawing.Pen]::new($black, 9), 16, 48, 224, 142)

    $bat = [System.Drawing.Drawing2D.GraphicsPath]::new()
    [double[][]]$points = @(
        @(-1.00, -0.05), @(-0.82, -0.36), @(-0.55, -0.35), @(-0.36, -0.58),
        @(-0.24, -0.16), @(-0.10, -0.70), @(0.00, -0.23), @(0.10, -0.70),
        @(0.24, -0.16), @(0.36, -0.58), @(0.55, -0.35), @(0.82, -0.36),
        @(1.00, -0.05), @(0.78, 0.18), @(0.53, 0.28), @(0.38, 0.12),
        @(0.27, 0.45), @(0.10, 0.54), @(0.00, 0.28), @(-0.10, 0.54),
        @(-0.27, 0.45), @(-0.38, 0.12), @(-0.53, 0.28), @(-0.78, 0.18)
    )
    Add-PolygonPath $bat $points 128 122 95 82
    $g.FillPath([System.Drawing.SolidBrush]::new($black), $bat)
    $bat.Dispose()
    Save-Png $canvas (Join-Path $textureDir "bat_sigil.png")
}

function New-WarriorSigil {
    $canvas = New-Canvas
    $g = $canvas.Graphics
    $dark = [System.Drawing.Color]::FromArgb(255, 15, 15, 18)
    $red = [System.Drawing.Color]::FromArgb(255, 105, 0, 18)
    $glow = [System.Drawing.Color]::FromArgb(150, 255, 235, 225)
    $centerX = 128.0
    $centerY = 132.0

    for ($arm = 0; $arm -lt 3; $arm++) {
        $rotation = (2.0 * [Math]::PI * $arm / 3.0) - ([Math]::PI / 2.0)
        $cos = [Math]::Cos($rotation)
        $sin = [Math]::Sin($rotation)
        $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
        $started = $false
        for ($i = 0; $i -le 120; $i++) {
            $t = 2.0 * [Math]::PI * $i / 120.0
            $lx = 31.0 * [Math]::Sin($t)
            $ly = 58.0 * [Math]::Cos($t) + 34.0
            $x = [single]($centerX + $lx * $cos - $ly * $sin)
            $y = [single]($centerY + $lx * $sin + $ly * $cos)
            if (!$started) {
                $path.StartFigure()
                $started = $true
            } else {
                $path.AddLine($lastX, $lastY, $x, $y)
            }
            $lastX = $x
            $lastY = $y
        }
        $path.CloseFigure()
        $g.DrawPath([System.Drawing.Pen]::new($glow, 20), $path)
        $g.DrawPath([System.Drawing.Pen]::new($dark, 14), $path)
        $g.DrawPath([System.Drawing.Pen]::new($red, 7), $path)
        $path.Dispose()
    }
    Save-Png $canvas (Join-Path $textureDir "warrior_sigil.png")
}

function Write-SigilModel([string]$name) {
    $json = @"
{
  "ambientocclusion": false,
  "textures": {
    "sigil": "item/$name"
  },
  "elements": [
    {
      "from": [0, 0, 7.95],
      "to": [16, 16, 8.05],
      "faces": {
        "north": { "uv": [0, 0, 16, 16], "texture": "#sigil" },
        "south": { "uv": [16, 0, 0, 16], "texture": "#sigil" }
      }
    }
  ]
}
"@
    [System.IO.File]::WriteAllText((Join-Path $modelDir "$name.json"), $json, [System.Text.UTF8Encoding]::new($false))
}

New-BatSigil
New-WarriorSigil
Write-SigilModel "bat_sigil"
Write-SigilModel "warrior_sigil"

Write-Host "Generated quantum sigil textures and models."
