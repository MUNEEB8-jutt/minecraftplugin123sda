param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$textureDir = Join-Path $ProjectRoot "src/main/resources/rp/textures/item"
$modelDir = Join-Path $ProjectRoot "src/main/resources/rp/models/item"
$bbDir = Join-Path $ProjectRoot "src/main/resources/rp/blockbench"
New-Item -ItemType Directory -Force -Path $textureDir, $modelDir, $bbDir | Out-Null

$texturePath = Join-Path $textureDir "ritual_core.png"
$modelPath = Join-Path $modelDir "ritual_core.json"
$bbPath = Join-Path $bbDir "ritual_core.bbmodel"

$size = 128
$bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::FromArgb(0,0,0,0))

# Main glow aura
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddEllipse(12, 12, 104, 104)
$auraBrush = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
$auraBrush.CenterColor = [System.Drawing.Color]::FromArgb(190, 199, 120, 255)
$auraBrush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 96, 32, 148))
$g.FillPath($auraBrush, $path)
$auraBrush.Dispose()
$path.Dispose()

# Outer ritual ring
$outerPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(230, 163, 90, 255), 7)
$outerPen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$g.DrawEllipse($outerPen, 18, 18, 92, 92)
$outerPen.Dispose()

# Inner ring + runes
$innerPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(220, 116, 58, 182), 3)
$g.DrawEllipse($innerPen, 30, 30, 68, 68)
$innerPen.Dispose()

for ($i = 0; $i -lt 12; $i++) {
    $a = ($i / 12.0) * [Math]::PI * 2.0
    $x = 64 + [Math]::Cos($a) * 38
    $y = 64 + [Math]::Sin($a) * 38
    $rune = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(240, 212, 156, 255))
    $g.FillEllipse($rune, $x - 2.2, $y - 2.2, 4.4, 4.4)
    $rune.Dispose()
}

# Core crystal body (faceted)
$poly1 = @(
    [System.Drawing.PointF]::new(64, 22),
    [System.Drawing.PointF]::new(84, 56),
    [System.Drawing.PointF]::new(64, 106),
    [System.Drawing.PointF]::new(44, 56)
)
$poly2 = @(
    [System.Drawing.PointF]::new(64, 28),
    [System.Drawing.PointF]::new(76, 56),
    [System.Drawing.PointF]::new(64, 96),
    [System.Drawing.PointF]::new(52, 56)
)
$facetA = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(248, 110, 230, 255))
$facetB = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 78, 155, 232))
$g.FillPolygon($facetA, $poly1)
$g.FillPolygon($facetB, $poly2)
$facetA.Dispose()
$facetB.Dispose()

# Crystal edge highlights
$hiPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 230, 245, 255), 2)
$g.DrawLine($hiPen, 64, 24, 64, 101)
$g.DrawLine($hiPen, 64, 24, 82, 56)
$g.DrawLine($hiPen, 64, 24, 46, 56)
$g.DrawLine($hiPen, 64, 101, 82, 56)
$g.DrawLine($hiPen, 64, 101, 46, 56)
$hiPen.Dispose()

# Core center pulse
$centerPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$centerPath.AddEllipse(50, 50, 28, 28)
$pulseBrush = New-Object System.Drawing.Drawing2D.PathGradientBrush($centerPath)
$pulseBrush.CenterColor = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$pulseBrush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 255, 255, 255))
$g.FillPath($pulseBrush, $centerPath)
$pulseBrush.Dispose()
$centerPath.Dispose()

# Energy sparks
for ($i = 0; $i -lt 44; $i++) {
    $x = Get-Random -Minimum 18 -Maximum 110
    $y = Get-Random -Minimum 18 -Maximum 110
    $alpha = Get-Random -Minimum 120 -Maximum 235
    $spark = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($alpha, 220, 186, 255))
    $g.FillEllipse($spark, $x, $y, 2, 2)
    $spark.Dispose()
}

$g.Dispose()
$bmp.Save($texturePath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

$modelJson = @"
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "legendary:item/ritual_core",
    "particle": "legendary:item/ritual_core"
  },
  "display": {
    "thirdperson_righthand": { "rotation": [0, 84, -35], "translation": [0, 2.2, -2.2], "scale": [1.85, 1.85, 1.85] },
    "firstperson_righthand": { "rotation": [0, -90, 22], "translation": [1.15, 3.2, 1.15], "scale": [2.2, 2.2, 2.2] },
    "ground": { "translation": [0, 2.0, 0], "scale": [1.4, 1.4, 1.4] },
    "fixed": { "rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6] },
    "gui": { "rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.25, 1.25, 1.25] }
  }
}
"@

Set-Content -Path $modelPath -Value $modelJson -Encoding UTF8

$bbModel = @"
{
  "meta": { "format_version": "4.9", "model_format": "java_block", "box_uv": false },
  "name": "Ritual Core",
  "resolution": { "width": 128, "height": 128 },
  "elements": [
    { "name": "outer_shell", "from": [5.5, 1.5, 5.5], "to": [10.5, 14.5, 10.5] },
    { "name": "inner_core", "from": [6.6, 3.0, 6.6], "to": [9.4, 13.0, 9.4] },
    { "name": "ring_ns", "from": [7.2, 6.9, 3.2], "to": [8.8, 9.1, 12.8] },
    { "name": "ring_ew", "from": [3.2, 6.9, 7.2], "to": [12.8, 9.1, 8.8] },
    { "name": "base", "from": [5.0, 0.0, 5.0], "to": [11.0, 1.8, 11.0] }
  ]
}
"@

Set-Content -Path $bbPath -Value $bbModel -Encoding UTF8

Write-Host "Generated Ritual Core assets:"
Write-Host " - $texturePath"
Write-Host " - $modelPath"
Write-Host " - $bbPath"
