param(
    [string]$TextureSource = "src/main/resources/rp/assets/minecraft/textures/item/black_cubes.png",
    [string]$TextureOutput = "src/main/resources/rp/assets/minecraft/textures/item/red_cubes.png",
    [string]$ModelSource = "src/main/resources/rp/assets/minecraft/models/custom/black_cubes.json",
    [string]$ModelOutput = "src/main/resources/rp/assets/minecraft/models/custom/red_cubes.json",
    [int]$Red = 115,
    [int]$Green = 0,
    [int]$Blue = 18
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$textureSourcePath = (Resolve-Path $TextureSource).Path
$modelSourcePath = (Resolve-Path $ModelSource).Path
$textureOutputPath = Join-Path (Get-Location) $TextureOutput
$modelOutputPath = Join-Path (Get-Location) $ModelOutput

New-Item -ItemType Directory -Force -Path (Split-Path $textureOutputPath) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $modelOutputPath) | Out-Null

$sourceImage = [System.Drawing.Bitmap]::new($textureSourcePath)
$outputImage = [System.Drawing.Bitmap]::new($sourceImage.Width, $sourceImage.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

try {
    for ($y = 0; $y -lt $sourceImage.Height; $y++) {
        for ($x = 0; $x -lt $sourceImage.Width; $x++) {
            $pixel = $sourceImage.GetPixel($x, $y)
            if ($pixel.A -eq 0) {
                $outputImage.SetPixel($x, $y, $pixel)
                continue
            }

            $brightness = ($pixel.R + $pixel.G + $pixel.B) / 3
            if ($brightness -lt 90) {
                $outputImage.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $Red, $Green, $Blue))
            } else {
                $outputImage.SetPixel($x, $y, $pixel)
            }
        }
    }

    $outputImage.Save($textureOutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $sourceImage.Dispose()
    $outputImage.Dispose()
}

$modelJson = Get-Content $modelSourcePath -Raw
$modelJson = $modelJson -replace '"item/black_cubes"', '"item/red_cubes"'
[System.IO.File]::WriteAllText($modelOutputPath, $modelJson, [System.Text.UTF8Encoding]::new($false))

Write-Host "Generated red cube texture: $TextureOutput"
Write-Host "Generated red cube model:   $ModelOutput"
