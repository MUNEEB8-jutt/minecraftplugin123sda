param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path,
    [string]$OutputDir = "",
    [string]$MinecraftJar = ""
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $ProjectRoot "generated\recipe_images"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Resolve-MinecraftJarPath {
    param([string]$GivenPath)
    if (-not [string]::IsNullOrWhiteSpace($GivenPath) -and (Test-Path $GivenPath)) {
        return (Resolve-Path $GivenPath).Path
    }
    $versions = Join-Path $env:APPDATA ".minecraft\versions"
    if (-not (Test-Path $versions)) {
        return $null
    }
    $jars = Get-ChildItem -Path $versions -Recurse -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    if ($jars.Count -eq 0) {
        return $null
    }
    return $jars[0].FullName
}

$jarPath = Resolve-MinecraftJarPath -GivenPath $MinecraftJar
$zip = $null
if ($jarPath) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
}

$iconCache = @{}

function New-FallbackIcon {
    param([string]$Id)
    $bmp = New-Object System.Drawing.Bitmap 32, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $hash = [Math]::Abs($Id.GetHashCode())
    $r = 60 + ($hash % 140)
    $gComp = 60 + (($hash / 3) % 140)
    $b = 60 + (($hash / 7) % 140)
    $bg = [System.Drawing.Color]::FromArgb(255, [int]$r, [int]$gComp, [int]$b)
    $g.Clear($bg)
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(230, 20, 20, 20), 2)
    $g.DrawRectangle($pen, 1, 1, 30, 30)
    $pen.Dispose()
    $font = New-Object System.Drawing.Font("Consolas", 8, [System.Drawing.FontStyle]::Bold)
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(240, 245, 245, 245))
    $label = ($Id -replace "[^a-zA-Z0-9]", "").ToUpper()
    if ($label.Length -gt 3) { $label = $label.Substring(0, 3) }
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString($label, $font, $brush, (New-Object System.Drawing.RectangleF(0, 0, 32, 32)), $format)
    $brush.Dispose()
    $font.Dispose()
    $format.Dispose()
    $g.Dispose()
    return $bmp
}

function Get-ZipBitmap {
    param(
        [System.IO.Compression.ZipArchive]$Archive,
        [string[]]$EntryPaths
    )
    if (-not $Archive) { return $null }
    foreach ($path in $EntryPaths) {
        $entry = $Archive.GetEntry($path)
        if (-not $entry) { continue }
        $stream = $entry.Open()
        $ms = New-Object System.IO.MemoryStream
        $stream.CopyTo($ms)
        $stream.Dispose()
        $ms.Position = 0
        try {
            $bmp = New-Object System.Drawing.Bitmap $ms
            $clone = New-Object System.Drawing.Bitmap $bmp
            $bmp.Dispose()
            $ms.Dispose()
            return $clone
        } catch {
            $ms.Dispose()
        }
    }
    return $null
}

function Get-ItemIcon {
    param([string]$ItemId)
    if ([string]::IsNullOrWhiteSpace($ItemId) -or $ItemId -eq "air") {
        return $null
    }
    $id = $ItemId.ToLowerInvariant()
    if ($iconCache.ContainsKey($id)) {
        return $iconCache[$id]
    }

    $customTexture = Join-Path $ProjectRoot ("src/main/resources/rp/textures/item/" + $id + ".png")
    if (Test-Path $customTexture) {
        $bmp = New-Object System.Drawing.Bitmap $customTexture
        $iconCache[$id] = $bmp
        return $bmp
    }

    $bundleTexture = Join-Path $ProjectRoot ("src/main/resources/rp/textures/item/" + $id + "/" + $id + ".png")
    if (Test-Path $bundleTexture) {
        $bmp = New-Object System.Drawing.Bitmap $bundleTexture
        $iconCache[$id] = $bmp
        return $bmp
    }

    $vanilla = Get-ZipBitmap -Archive $zip -EntryPaths @(
        "assets/minecraft/textures/item/$id.png",
        "assets/minecraft/textures/item/${id}_item.png",
        "assets/minecraft/textures/block/$id.png"
    )
    if ($vanilla) {
        $iconCache[$id] = $vanilla
        return $vanilla
    }

    $fallback = New-FallbackIcon -Id $id
    $iconCache[$id] = $fallback
    return $fallback
}

function Format-ItemLabel {
    param([string]$ItemId)
    if ([string]::IsNullOrWhiteSpace($ItemId) -or $ItemId -eq "air") {
        return ""
    }
    $raw = $ItemId -replace "^minecraft:", "" -replace "^legendary:", ""
    $raw = $raw -replace "_", " "
    $parts = $raw.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object {
        if ($_.Length -le 1) { $_.ToUpperInvariant() } else { $_.Substring(0,1).ToUpperInvariant() + $_.Substring(1).ToLowerInvariant() }
    }
    return ($parts -join " ")
}

function Draw-LabelInSlot {
    param(
        [System.Drawing.Graphics]$G,
        [string]$Label,
        [int]$X,
        [int]$Y,
        [int]$SlotSize,
        [int]$Count
    )
    $slotBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 52, 52, 58))
    $slotMid = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 68, 68, 76))
    $border = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 22, 22, 24), 2)
    $G.FillRectangle($slotBg, $X, $Y, $SlotSize, $SlotSize)
    $G.FillRectangle($slotMid, $X + 3, $Y + 3, $SlotSize - 6, $SlotSize - 6)
    $G.DrawRectangle($border, $X, $Y, $SlotSize - 1, $SlotSize - 1)
    $slotBg.Dispose()
    $slotMid.Dispose()
    $border.Dispose()

    if (-not [string]::IsNullOrWhiteSpace($Label)) {
        $textFont = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
        $textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(245, 242, 242, 250))
        $shadow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(180, 8, 8, 10))
        $format = New-Object System.Drawing.StringFormat
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        $rect = [System.Drawing.RectangleF]::new([single]($X + 4), [single]($Y + 4), [single]($SlotSize - 8), [single]($SlotSize - 8))
        $shadowRect = [System.Drawing.RectangleF]::new([single]($rect.X + 1), [single]($rect.Y + 1), [single]$rect.Width, [single]$rect.Height)
        $G.DrawString($Label, $textFont, $shadow, $shadowRect, $format)
        $G.DrawString($Label, $textFont, $textBrush, $rect, $format)
        $format.Dispose()
        $shadow.Dispose()
        $textBrush.Dispose()
        $textFont.Dispose()
    }

    if ($Count -gt 1) {
        $countFont = New-Object System.Drawing.Font("Consolas", 10, [System.Drawing.FontStyle]::Bold)
        $countBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(250, 245, 245, 245))
        $shadow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(210, 5, 5, 5))
        $text = "$Count"
        $tx = $X + $SlotSize - 18
        $ty = $Y + $SlotSize - 18
        $G.DrawString($text, $countFont, $shadow, $tx + 1, $ty + 1)
        $G.DrawString($text, $countFont, $countBrush, $tx, $ty)
        $shadow.Dispose()
        $countBrush.Dispose()
        $countFont.Dispose()
    }
}

function Render-RecipeImage {
    param(
        [hashtable]$Recipe,
        [string]$OutPath
    )

    $width = 720
    $height = 290
    $slot = 60
    $gap = 8
    $gridX = 40
    $gridY = 80
    $outX = 500
    $outY = 122

    $bmp = New-Object System.Drawing.Bitmap $width, $height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle(0, 0, $width, $height)),
        [System.Drawing.Color]::FromArgb(255, 24, 23, 30),
        [System.Drawing.Color]::FromArgb(255, 38, 36, 52),
        30.0
    )
    $g.FillRectangle($bg, 0, 0, $width, $height)
    $bg.Dispose()

    $titleFont = New-Object System.Drawing.Font("Segoe UI", 20, [System.Drawing.FontStyle]::Bold)
    $subFont = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Regular)
    $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 244, 226, 255))
    $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 194, 194, 208))
    $g.DrawString($Recipe.Name, $titleFont, $titleBrush, 34, 18)
    $g.DrawString("3x3 Crafting Recipe", $subFont, $subBrush, 36, 52)
    $titleFont.Dispose()
    $subFont.Dispose()
    $titleBrush.Dispose()
    $subBrush.Dispose()

    for ($r = 0; $r -lt 3; $r++) {
        for ($c = 0; $c -lt 3; $c++) {
            $item = $Recipe.Grid[$r][$c]
            $count = 1
            if ($item -is [hashtable]) {
                $count = [int]$item.Count
                $item = $item.Item
            }
            $label = Format-ItemLabel -ItemId $item
            $x = $gridX + ($c * ($slot + $gap))
            $y = $gridY + ($r * ($slot + $gap))
            Draw-LabelInSlot -G $g -Label $label -X $x -Y $y -SlotSize $slot -Count $count
        }
    }

    # Arrow to output
    $arrowPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 238, 224, 255), 5)
    $g.DrawLine($arrowPen, 300, 155, 455, 155)
    $g.DrawLine($arrowPen, 430, 135, 455, 155)
    $g.DrawLine($arrowPen, 430, 175, 455, 155)
    $arrowPen.Dispose()

    $outItem = $Recipe.Output
    $outCount = [int]$Recipe.OutputCount
    if ($outCount -lt 1) { $outCount = 1 }
    $outLabel = if ($Recipe.ContainsKey("OutputLabel") -and -not [string]::IsNullOrWhiteSpace($Recipe.OutputLabel)) {
        $Recipe.OutputLabel
    } else {
        Format-ItemLabel -ItemId $outItem
    }
    Draw-LabelInSlot -G $g -Label $outLabel -X $outX -Y $outY -SlotSize $slot -Count $outCount

    $labelFont = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
    $labelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 220, 220, 235))
    $g.DrawString("Output", $labelFont, $labelBrush, $outX + 4, $outY - 24)
    $labelBrush.Dispose()
    $labelFont.Dispose()

    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 80, 78, 102), 2)
    $g.DrawRectangle($borderPen, 5, 5, $width - 11, $height - 11)
    $borderPen.Dispose()

    $g.Dispose()
    $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

function Render-RecipeSheet {
    param(
        [array]$Cards,
        [string]$OutPath
    )
    if ($Cards.Count -eq 0) { return }
    $columns = 2
    $cardW = 720
    $cardH = 290
    $pad = 18
    $rows = [Math]::Ceiling($Cards.Count / $columns)
    $width = ($columns * $cardW) + (($columns + 1) * $pad)
    $height = ($rows * $cardH) + (($rows + 1) * $pad)
    $sheet = New-Object System.Drawing.Bitmap $width, $height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($sheet)
    $g.Clear([System.Drawing.Color]::FromArgb(255, 14, 13, 18))
    for ($i = 0; $i -lt $Cards.Count; $i++) {
        $row = [Math]::Floor($i / $columns)
        $col = $i % $columns
        $x = $pad + ($col * ($cardW + $pad))
        $y = $pad + ($row * ($cardH + $pad))
        $card = New-Object System.Drawing.Bitmap $Cards[$i]
        $g.DrawImage($card, $x, $y, $cardW, $cardH)
        $card.Dispose()
    }
    $g.Dispose()
    $sheet.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $sheet.Dispose()
}

# ---------------------------------------------------------------------------
# Recipe definitions
# Add more recipes here in the same format.
# Item ids should match vanilla texture ids or custom ids in rp/textures/item.
# ---------------------------------------------------------------------------
$recipes = @(
    @{
        Id = "ritual_core_emerald_dagger"
        Name = "Emerald Dagger Ritual Core"
        Output = "ritual_core_emerald_dagger"
        OutputCount = 1
        OutputLabel = "Emerald Dagger Ritual Core"
        Grid = @(
            @("emerald_block", "amethyst_shard", "emerald_block"),
            @("gold_block", "nether_star", "gold_block"),
            @("emerald_block", "amethyst_shard", "emerald_block")
        )
    },
    @{
        Id = "ritual_core_silk_crossbow"
        Name = "Silk Crossbow Ritual Core"
        Output = "ritual_core_silk_crossbow"
        OutputCount = 1
        OutputLabel = "Silk Crossbow Ritual Core"
        Grid = @(
            @("cobweb", "string", "cobweb"),
            @("tripwire_hook", "nether_star", "tripwire_hook"),
            @("cobweb", "string", "cobweb")
        )
    },
    @{
        Id = "ritual_core_blizzard_bow"
        Name = "Blizzard Bow Ritual Core"
        Output = "ritual_core_blizzard_bow"
        OutputCount = 1
        OutputLabel = "Blizzard Bow Ritual Core"
        Grid = @(
            @("packed_ice", "snow_block", "packed_ice"),
            @("arrow", "nether_star", "arrow"),
            @("packed_ice", "snow_block", "packed_ice")
        )
    },
    @{
        Id = "ritual_core_soul_scythe"
        Name = "Soul Scythe Ritual Core"
        Output = "ritual_core_soul_scythe"
        OutputCount = 1
        OutputLabel = "Soul Scythe Ritual Core"
        Grid = @(
            @("crying_obsidian", "soul_sand", "crying_obsidian"),
            @("ghast_tear", "nether_star", "ghast_tear"),
            @("crying_obsidian", "soul_sand", "crying_obsidian")
        )
    },
    @{
        Id = "ritual_core_hell_bringer"
        Name = "Hell Bringer Ritual Core"
        Output = "ritual_core_hell_bringer"
        OutputCount = 1
        OutputLabel = "Hell Bringer Ritual Core"
        Grid = @(
            @("magma_block", "blaze_rod", "magma_block"),
            @("fire_charge", "nether_star", "fire_charge"),
            @("magma_block", "blaze_rod", "magma_block")
        )
    },
    @{
        Id = "ritual_core_stone_breaker"
        Name = "Stone Breaker Ritual Core"
        Output = "ritual_core_stone_breaker"
        OutputCount = 1
        OutputLabel = "Stone Breaker Ritual Core"
        Grid = @(
            @("tuff", "iron_block", "tuff"),
            @("breeze_rod", "nether_star", "breeze_rod"),
            @("tuff", "iron_block", "tuff")
        )
    },
    @{
        Id = "ritual_core_thunder_glaive"
        Name = "Thunder Glaive Ritual Core"
        Output = "ritual_core_thunder_glaive"
        OutputCount = 1
        OutputLabel = "Thunder Glaive Ritual Core"
        Grid = @(
            @("ender_eye", "copper_block", "ender_eye"),
            @("lightning_rod", "nether_star", "lightning_rod"),
            @("ender_eye", "copper_block", "ender_eye")
        )
    },
    @{
        Id = "ritual_core_warden_katana"
        Name = "Warden Katana Ritual Core"
        Output = "ritual_core_warden_katana"
        OutputCount = 1
        OutputLabel = "Warden Katana Ritual Core"
        Grid = @(
            @("sculk_catalyst", "echo_shard", "sculk_catalyst"),
            @("deepslate_tiles", "nether_star", "deepslate_tiles"),
            @("sculk_catalyst", "echo_shard", "sculk_catalyst")
        )
    }
)

# Cleanup old generated recipe cards before re-render.
Get-ChildItem -Path $OutputDir -Filter "*_recipe.png" -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

$renderedCards = @()
foreach ($recipe in $recipes) {
    $out = Join-Path $OutputDir ($recipe.Id + "_recipe.png")
    Render-RecipeImage -Recipe $recipe -OutPath $out
    $renderedCards += $out
}

$sheetPath = Join-Path $OutputDir "all_recipes_sheet.png"
Render-RecipeSheet -Cards $renderedCards -OutPath $sheetPath

if ($zip) { $zip.Dispose() }
foreach ($bmp in $iconCache.Values) {
    if ($bmp) { $bmp.Dispose() }
}

Write-Host "Recipe images generated in: $OutputDir"
Write-Host "Combined sheet: $sheetPath"
Write-Host "Render mode: Item names in slots (no icons)"
if ($jarPath) {
    Write-Host "Detected Minecraft jar: $jarPath"
} else {
    Write-Host "Minecraft jar not found (not required in name-only mode)."
}
