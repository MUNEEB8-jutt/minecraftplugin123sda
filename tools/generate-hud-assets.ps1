param(
    [string]$RitualSource = 'C:\Users\Muneeb PC\Downloads\ChatGPT Image Jun 2, 2026, 07_10_01 PM-Photoroom.png',
    [string]$CooldownSource = 'C:\Users\Muneeb PC\Downloads\ChatGPT Image Jun 2, 2026, 07_11_47 PM-Photoroom.png'
)

Add-Type -AssemblyName System.Drawing

$outputDir = Join-Path $PSScriptRoot '..\src\main\resources\hud'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function Export-CleanFrame {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][int]$Width,
        [int]$VisibleAlpha = 8
    )

    if (-not (Test-Path -LiteralPath $Source)) {
        throw "HUD source image not found: $Source"
    }

    $sourceBitmap = [System.Drawing.Bitmap]::FromFile($Source)
    try {
        $minX = $sourceBitmap.Width
        $minY = $sourceBitmap.Height
        $maxX = -1
        $maxY = -1
        for ($y = 0; $y -lt $sourceBitmap.Height; $y++) {
            for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
                if ($sourceBitmap.GetPixel($x, $y).A -ge $VisibleAlpha) {
                    if ($x -lt $minX) { $minX = $x }
                    if ($x -gt $maxX) { $maxX = $x }
                    if ($y -lt $minY) { $minY = $y }
                    if ($y -gt $maxY) { $maxY = $y }
                }
            }
        }
        if ($maxX -lt 0) {
            throw "HUD source image has no visible pixels: $Source"
        }

        $contentWidth = $maxX - $minX + 1
        $contentHeight = $maxY - $minY + 1
        $height = [Math]::Max(1, [int][Math]::Round($contentHeight * ($Width / [double]$contentWidth)))
        $scaled = New-Object System.Drawing.Bitmap $Width, $height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($scaled)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.DrawImage(
                    $sourceBitmap,
                    (New-Object System.Drawing.Rectangle 0, 0, $Width, $height),
                    (New-Object System.Drawing.Rectangle $minX, $minY, $contentWidth, $contentHeight),
                    [System.Drawing.GraphicsUnit]::Pixel
                )
            } finally {
                $graphics.Dispose()
            }
            $scaled.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $scaled.Dispose()
        }
    } finally {
        $sourceBitmap.Dispose()
    }
}

function Export-Slices {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Prefix,
        [Parameter(Mandatory = $true)][int]$SliceWidth
    )

    $sourceBitmap = [System.Drawing.Bitmap]::FromFile($Source)
    try {
        $sliceCount = [int][Math]::Ceiling($sourceBitmap.Width / [double]$SliceWidth)
        for ($index = 0; $index -lt $sliceCount; $index++) {
            $x = $index * $SliceWidth
            $width = [Math]::Min($SliceWidth, $sourceBitmap.Width - $x)
            $slice = New-Object System.Drawing.Bitmap $width, $sourceBitmap.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                $graphics = [System.Drawing.Graphics]::FromImage($slice)
                try {
                    $graphics.Clear([System.Drawing.Color]::Transparent)
                    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                    $graphics.DrawImage(
                        $sourceBitmap,
                        (New-Object System.Drawing.Rectangle 0, 0, $width, $sourceBitmap.Height),
                        (New-Object System.Drawing.Rectangle $x, 0, $width, $sourceBitmap.Height),
                        [System.Drawing.GraphicsUnit]::Pixel
                    )
                } finally {
                    $graphics.Dispose()
                }

                # BetterHUD trims fully transparent image borders. Preserve the
                # exact authored dimensions so adjacent slices remain seamless.
                $anchor = [System.Drawing.Color]::FromArgb(1, 0, 0, 0)
                $slice.SetPixel(0, 0, $anchor)
                $slice.SetPixel($width - 1, 0, $anchor)
                $slice.SetPixel(0, $sourceBitmap.Height - 1, $anchor)
                $slice.SetPixel($width - 1, $sourceBitmap.Height - 1, $anchor)
                $slice.Save((Join-Path $outputDir "$Prefix$($index + 1).png"), [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $slice.Dispose()
            }
        }
    } finally {
        $sourceBitmap.Dispose()
    }
}

function Export-Tiles {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Prefix,
        [Parameter(Mandatory = $true)][int]$TileWidth,
        [Parameter(Mandatory = $true)][int]$TileHeight
    )

    Get-ChildItem -LiteralPath $outputDir -Filter "$Prefix*.png" -File | Remove-Item -Force
    $sourceBitmap = [System.Drawing.Bitmap]::FromFile($Source)
    try {
        $columns = [int][Math]::Ceiling($sourceBitmap.Width / [double]$TileWidth)
        $rows = [int][Math]::Ceiling($sourceBitmap.Height / [double]$TileHeight)
        $index = 1
        for ($row = 0; $row -lt $rows; $row++) {
            for ($column = 0; $column -lt $columns; $column++) {
                $x = $column * $TileWidth
                $y = $row * $TileHeight
                $width = [Math]::Min($TileWidth, $sourceBitmap.Width - $x)
                $height = [Math]::Min($TileHeight, $sourceBitmap.Height - $y)
                $tile = New-Object System.Drawing.Bitmap $width, $height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                try {
                    $graphics = [System.Drawing.Graphics]::FromImage($tile)
                    try {
                        $graphics.Clear([System.Drawing.Color]::Transparent)
                        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                        $graphics.DrawImage(
                            $sourceBitmap,
                            (New-Object System.Drawing.Rectangle 0, 0, $width, $height),
                            (New-Object System.Drawing.Rectangle $x, $y, $width, $height),
                            [System.Drawing.GraphicsUnit]::Pixel
                        )
                    } finally {
                        $graphics.Dispose()
                    }

                    # BetterHUD trims fully transparent image borders. Preserve the
                    # authored tile size so the reconstructed frame remains seamless.
                    $anchor = [System.Drawing.Color]::FromArgb(1, 0, 0, 0)
                    $tile.SetPixel(0, 0, $anchor)
                    $tile.SetPixel($width - 1, 0, $anchor)
                    $tile.SetPixel(0, $height - 1, $anchor)
                    $tile.SetPixel($width - 1, $height - 1, $anchor)
                    $tile.Save((Join-Path $outputDir "$Prefix$index.png"), [System.Drawing.Imaging.ImageFormat]::Png)
                } finally {
                    $tile.Dispose()
                }
                $index++
            }
        }
    } finally {
        $sourceBitmap.Dispose()
    }
}

function Export-MonochromeFrame {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    $sourceBitmap = [System.Drawing.Bitmap]::FromFile($Source)
    try {
        $output = New-Object System.Drawing.Bitmap $sourceBitmap.Width, $sourceBitmap.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            for ($y = 0; $y -lt $sourceBitmap.Height; $y++) {
                for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
                    $pixel = $sourceBitmap.GetPixel($x, $y)
                    if ($pixel.A -eq 0) {
                        continue
                    }
                    $luminance = [int][Math]::Round(($pixel.R * 0.24) + ($pixel.G * 0.68) + ($pixel.B * 0.08))
                    $grey = [Math]::Max(64, [Math]::Min(238, [int][Math]::Round(($luminance * 0.72) + 62)))
                    $output.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $grey, $grey, $grey))
                }
            }
            $output.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $output.Dispose()
        }
    } finally {
        $sourceBitmap.Dispose()
    }
}

function Export-HourglassIcon {
    param(
        [Parameter(Mandatory = $true)][string]$Destination
    )

    $icon = New-Object System.Drawing.Bitmap 24, 24, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($icon)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $outline = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 122, 248, 255)), 3
            $sand = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 80, 235, 255))
            try {
                $graphics.DrawLine($outline, 5, 3, 19, 3)
                $graphics.DrawLine($outline, 5, 21, 19, 21)
                $graphics.DrawLine($outline, 6, 4, 6, 7)
                $graphics.DrawLine($outline, 18, 4, 18, 7)
                $graphics.DrawLine($outline, 6, 17, 6, 20)
                $graphics.DrawLine($outline, 18, 17, 18, 20)
                $graphics.DrawLine($outline, 7, 6, 17, 18)
                $graphics.DrawLine($outline, 17, 6, 7, 18)
                $graphics.FillPolygon($sand, [System.Drawing.Point[]]@(
                    (New-Object System.Drawing.Point 8, 6),
                    (New-Object System.Drawing.Point 16, 6),
                    (New-Object System.Drawing.Point 12, 11)
                ))
                $graphics.FillPolygon($sand, [System.Drawing.Point[]]@(
                    (New-Object System.Drawing.Point 12, 13),
                    (New-Object System.Drawing.Point 16, 18),
                    (New-Object System.Drawing.Point 8, 18)
                ))
            } finally {
                $outline.Dispose()
                $sand.Dispose()
            }
        } finally {
            $graphics.Dispose()
        }
        $icon.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $icon.Dispose()
    }
}

$ritualOut = Join-Path $outputDir 'ritual_frame.png'
Export-CleanFrame -Source $RitualSource -Destination $ritualOut -Width 840
Export-Tiles -Source $ritualOut -Prefix 'ritual_frame_' -TileWidth 210 -TileHeight 150
Export-HourglassIcon -Destination (Join-Path $outputDir 'ritual_timer_icon.png')

$cooldownOut = Join-Path $outputDir 'cooldown_frame.png'
Export-CleanFrame -Source $CooldownSource -Destination $cooldownOut -Width 660
Export-Slices -Source $cooldownOut -Prefix 'cooldown_frame_' -SliceWidth 220

Get-ChildItem -LiteralPath $outputDir -Filter '*_mask*.png' -File | Remove-Item -Force
Get-ChildItem -LiteralPath $outputDir -File | Select-Object Name, Length
