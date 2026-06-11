param(
    [string]$MinecraftJar = "$env:APPDATA\.minecraft\versions\1.21.11\1.21.11.jar",
    [string]$OutputRoot = "$PSScriptRoot\..\src\main\resources\rp\assets\minecraft\textures\gui\sprites\hud\heart"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sprites = @(
    "withered_full.png",
    "withered_full_blinking.png",
    "withered_half.png",
    "withered_half_blinking.png",
    "withered_hardcore_full.png",
    "withered_hardcore_full_blinking.png",
    "withered_hardcore_half.png",
    "withered_hardcore_half_blinking.png"
)

$targetRoot = [System.IO.Path]::GetFullPath($OutputRoot)
[System.IO.Directory]::CreateDirectory($targetRoot) | Out-Null
$zip = [System.IO.Compression.ZipFile]::OpenRead([System.IO.Path]::GetFullPath($MinecraftJar))

try {
    foreach ($sprite in $sprites) {
        $entryName = "assets/minecraft/textures/gui/sprites/hud/heart/$sprite"
        $entry = $zip.GetEntry($entryName)
        if ($null -eq $entry) {
            throw "Missing vanilla sprite: $entryName"
        }

        $stream = $entry.Open()
        try {
            $source = [System.Drawing.Bitmap]::FromStream($stream)
            try {
                $output = [System.Drawing.Bitmap]::new($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                try {
                    for ($y = 0; $y -lt $source.Height; $y++) {
                        for ($x = 0; $x -lt $source.Width; $x++) {
                            $pixel = $source.GetPixel($x, $y)
                            if ($pixel.A -eq 0) {
                                $output.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                                continue
                            }

                            $brightness = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B)) / 255.0
                            $red = [Math]::Min(255, [int](58 + 142 * $brightness))
                            $green = [Math]::Min(255, [int](4 + 20 * $brightness))
                            $blue = [Math]::Min(255, [int](10 + 28 * $brightness))
                            $output.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $red, $green, $blue))
                        }
                    }
                    $output.Save([System.IO.Path]::Combine($targetRoot, $sprite), [System.Drawing.Imaging.ImageFormat]::Png)
                } finally {
                    $output.Dispose()
                }
            } finally {
                $source.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    }
} finally {
    $zip.Dispose()
}

Write-Output "Generated $($sprites.Count) dark-red Wither HUD sprites in $targetRoot"
