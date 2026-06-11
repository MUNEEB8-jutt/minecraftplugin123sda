$ErrorActionPreference = 'Stop'

$modelRoot = Join-Path $PSScriptRoot "..\\src\\main\\resources\\rp\\models\\item"
$bbRoot = Join-Path $PSScriptRoot "..\\src\\main\\resources\\rp\\blockbench"
New-Item -ItemType Directory -Path $modelRoot -Force | Out-Null
New-Item -ItemType Directory -Path $bbRoot -Force | Out-Null

function Clamp([double]$v) {
    if ($v -lt 0.0) { return 0.0 }
    if ($v -gt 16.0) { return 16.0 }
    return [math]::Round($v, 4)
}

function New-Faces($tex) {
    return [ordered]@{
        north = @{ uv = @(0, 0, 16, 16); texture = $tex }
        east = @{ uv = @(0, 0, 16, 16); texture = $tex }
        south = @{ uv = @(0, 0, 16, 16); texture = $tex }
        west = @{ uv = @(0, 0, 16, 16); texture = $tex }
        up = @{ uv = @(0, 0, 16, 16); texture = $tex }
        down = @{ uv = @(0, 0, 16, 16); texture = $tex }
    }
}

function Snap-Angle([double]$angle) {
    $allowed = @(-45.0, -22.5, 0.0, 22.5, 45.0)
    $best = $allowed[0]
    $dist = [math]::Abs($angle - $best)
    foreach ($a in $allowed) {
        $d = [math]::Abs($angle - $a)
        if ($d -lt $dist) {
            $best = $a
            $dist = $d
        }
    }
    return $best
}

function Add-Cube {
    param(
        [System.Collections.Generic.List[object]]$ModelElements,
        [System.Collections.Generic.List[object]]$BbElements,
        [double]$fx, [double]$fy, [double]$fz,
        [double]$tx, [double]$ty, [double]$tz,
        [string]$name = "part",
        [hashtable]$rotation = $null
    )

    $from = @($(Clamp $fx), $(Clamp $fy), $(Clamp $fz))
    $to = @($(Clamp $tx), $(Clamp $ty), $(Clamp $tz))
    if ($to[0] -le $from[0]) { $to[0] = Clamp($from[0] + 0.2) }
    if ($to[1] -le $from[1]) { $to[1] = Clamp($from[1] + 0.2) }
    if ($to[2] -le $from[2]) { $to[2] = Clamp($from[2] + 0.2) }

    $modelElement = [ordered]@{
        from = $from
        to = $to
        faces = New-Faces "#0"
    }

    $bbElement = [ordered]@{
        name = "$name`_$($BbElements.Count)"
        uuid = [guid]::NewGuid().ToString()
        from = $from
        to = $to
        faces = @{}
    }

    if ($rotation) {
        $axis = [string]$rotation.axis
        $origin = @([double]$rotation.origin[0], [double]$rotation.origin[1], [double]$rotation.origin[2])
        $angle = Snap-Angle([double]$rotation.angle)
        $modelElement.rotation = @{
            origin = $origin
            axis = $axis
            angle = $angle
        }
        $bbElement.origin = $origin
        if ($axis -eq 'x') { $bbElement.rotation = @($angle, 0, 0) }
        elseif ($axis -eq 'y') { $bbElement.rotation = @(0, $angle, 0) }
        else { $bbElement.rotation = @(0, 0, $angle) }
    }

    $ModelElements.Add($modelElement)
    $BbElements.Add($bbElement)
}

function Add-Rod {
    param(
        [System.Collections.Generic.List[object]]$M,
        [System.Collections.Generic.List[object]]$B,
        [double]$x1, [double]$x2, [double]$z1, [double]$z2,
        [double]$y1, [double]$y2, [double]$seg,
        [string]$name = "rod"
    )
    $y = $y1
    while ($y -lt $y2) {
        $yn = [math]::Min($y + $seg, $y2)
        Add-Cube $M $B $x1 $y $z1 $x2 $yn $z2 $name
        $y = $yn
    }
}

function Add-TaperBlade {
    param(
        [System.Collections.Generic.List[object]]$M,
        [System.Collections.Generic.List[object]]$B,
        [double]$cx, [double]$cz,
        [double]$y1, [double]$y2,
        [int]$segments,
        [double]$baseWidth, [double]$tipWidth,
        [double]$depth,
        [double]$xCurve = 0.0,
        [string]$name = "blade"
    )
    for ($i = 0; $i -lt $segments; $i++) {
        $t0 = $i / [double]$segments
        $t1 = ($i + 1) / [double]$segments
        $w = $baseWidth + ($tipWidth - $baseWidth) * (($t0 + $t1) / 2.0)
        $yc0 = $y1 + ($y2 - $y1) * $t0
        $yc1 = $y1 + ($y2 - $y1) * $t1
        $curve = $xCurve * (($t0 + $t1) / 2.0) * (($t0 + $t1) / 2.0)
        $x1 = ($cx + $curve) - ($w / 2.0)
        $x2 = ($cx + $curve) + ($w / 2.0)
        Add-Cube $M $B $x1 $yc0 ($cz - $depth / 2.0) $x2 $yc1 ($cz + $depth / 2.0) $name
    }
}

function Build-EmeraldDagger {
    param($M, $B)
    Add-Cube $M $B 7.1 0.0 7.1 8.9 0.8 8.9 "pommel"
    Add-Rod $M $B 7.45 8.55 7.35 8.65 0.8 5.2 0.55 "grip"
    Add-Cube $M $B 5.1 5.2 7.1 10.9 5.9 8.9 "guard"
    Add-Cube $M $B 6.0 5.0 7.2 7.2 5.8 8.8 "guard_l"
    Add-Cube $M $B 8.8 5.0 7.2 10.0 5.8 8.8 "guard_r"
    Add-TaperBlade $M $B 8.0 8.0 5.9 15.5 18 2.0 0.35 1.1 0.0 "blade"
    Add-Rod $M $B 7.92 8.08 7.05 8.95 6.3 15.3 0.8 "ridge"
}

function Build-SilkCrossbow {
    param($M, $B)
    Add-Cube $M $B 7.2 0.0 7.2 8.8 0.8 8.8 "pommel"
    Add-Rod $M $B 7.35 8.65 7.25 8.75 0.8 6.0 0.6 "grip"
    Add-Cube $M $B 7.2 5.8 4.2 8.8 7.2 11.8 "stock"
    Add-Cube $M $B 6.4 7.2 5.1 9.6 9.0 10.9 "body"
    for ($i = 0; $i -lt 10; $i++) {
        $t = $i / 9.0
        $y0 = 8.1 + 1.2 * $t
        $y1 = $y0 + 0.35
        $z0 = 4.4 + 0.35 * $t
        $z1 = 11.6 - 0.35 * $t
        $xl = 6.2 - 3.6 * $t
        $xr = 9.8 + 3.6 * $t
        Add-Cube $M $B ($xl - 0.28) $y0 $z0 ($xl + 0.28) $y1 $z1 "limb_l"
        Add-Cube $M $B ($xr - 0.28) $y0 $z0 ($xr + 0.28) $y1 $z1 "limb_r"
    }
    Add-Rod $M $B 7.93 8.07 3.9 4.4 9.0 9.6 0.3 "bolt"
    Add-Rod $M $B 7.93 8.07 11.6 12.1 9.0 9.6 0.3 "bolt"
    Add-Rod $M $B 7.95 8.05 7.95 8.05 9.05 9.35 0.15 "rail"
    Add-Cube $M $B 2.15 8.7 4.8 2.45 8.9 11.2 "string_l"
    Add-Cube $M $B 13.55 8.7 4.8 13.85 8.9 11.2 "string_r"
}

function Build-BlizzardBow {
    param($M, $B)
    for ($i = 0; $i -lt 20; $i++) {
        $t0 = $i / 20.0
        $t1 = ($i + 1) / 20.0
        $y0 = 1.0 + 14.0 * $t0
        $y1 = 1.0 + 14.0 * $t1
        $curve0 = 2.7 * [math]::Sin([math]::PI * (($t0 + $t1) / 2.0))
        $half = 0.28 + 0.06 * [math]::Abs(0.5 - (($t0 + $t1) / 2.0))
        Add-Cube $M $B (7.1 - $curve0 - $half) $y0 7.2 (7.1 - $curve0 + $half) $y1 8.8 "limb_l"
        Add-Cube $M $B (8.9 + $curve0 - $half) $y0 7.2 (8.9 + $curve0 + $half) $y1 8.8 "limb_r"
    }
    Add-Rod $M $B 7.35 8.65 7.2 8.8 5.4 10.6 0.65 "grip"
    Add-Rod $M $B 7.96 8.04 7.96 8.04 1.0 15.0 0.7 "string"
    Add-Cube $M $B 6.9 7.0 7.05 9.1 7.6 8.95 "core"
    Add-Cube $M $B 6.9 8.4 7.05 9.1 9.0 8.95 "core"
}

function Build-SoulScythe {
    param($M, $B)
    Add-Cube $M $B 7.0 0.0 7.0 9.0 0.8 9.0 "pommel"
    Add-Rod $M $B 7.55 8.45 7.55 8.45 0.8 15.2 0.7 "shaft"
    Add-Cube $M $B 7.1 14.7 6.8 8.9 15.5 9.2 "joint"
    for ($i = 0; $i -lt 18; $i++) {
        $t0 = $i / 18.0
        $t1 = ($i + 1) / 18.0
        $tm = ($t0 + $t1) / 2.0
        $x = 8.6 + 5.8 * $tm
        $y0 = 14.9 - 4.8 * $t0
        $y1 = 14.9 - 4.8 * $t1
        Add-Cube $M $B ($x - 0.28) $y1 7.2 ($x + 0.28) $y0 8.8 "blade"
    }
    for ($i = 0; $i -lt 8; $i++) {
        $t0 = $i / 8.0
        $t1 = ($i + 1) / 8.0
        $x = 8.3 - 2.2 * (($t0 + $t1) / 2.0)
        $y0 = 14.2 - 1.8 * $t0
        $y1 = 14.2 - 1.8 * $t1
        Add-Cube $M $B ($x - 0.2) $y1 7.3 ($x + 0.2) $y0 8.7 "hook"
    }
}

function Build-HellBringer {
    param($M, $B)
    Add-Cube $M $B 7.0 0.0 7.0 9.0 0.8 9.0 "pommel"
    Add-Rod $M $B 7.4 8.6 7.3 8.7 0.8 6.2 0.6 "grip"
    Add-Cube $M $B 5.0 6.2 7.1 11.0 6.9 8.9 "guard"
    Add-TaperBlade $M $B 8.0 8.0 6.9 15.6 18 2.4 0.55 1.15 0.0 "blade"
    for ($i = 0; $i -lt 12; $i++) {
        $t = $i / 11.0
        $y0 = 7.1 + 7.8 * $t
        $y1 = $y0 + 0.45
        $spread = 1.1 - 0.6 * $t
        Add-Cube $M $B (6.9 - $spread) $y0 7.3 (7.2 - $spread * 0.75) $y1 8.7 "spike_l"
        Add-Cube $M $B (8.8 + $spread * 0.75) $y0 7.3 (9.1 + $spread) $y1 8.7 "spike_r"
    }
    Add-Rod $M $B 7.92 8.08 6.9 9.1 7.2 15.4 0.85 "ridge"
}

function Build-StoneBreaker {
    param($M, $B)
    Add-Cube $M $B 6.8 0.0 6.8 9.2 0.9 9.2 "pommel"
    Add-Rod $M $B 7.35 8.65 7.35 8.65 0.9 8.2 0.65 "shaft"
    Add-Cube $M $B 4.9 8.2 5.1 11.1 11.6 10.9 "head"
    Add-Cube $M $B 3.4 8.5 5.6 4.9 11.3 10.4 "cap_l"
    Add-Cube $M $B 11.1 8.5 5.6 12.6 11.3 10.4 "cap_r"
    Add-Cube $M $B 5.4 11.6 5.6 10.6 12.4 10.4 "crest"
    for ($i = 0; $i -lt 6; $i++) {
        $z0 = 5.5 + $i * 0.9
        Add-Cube $M $B 5.0 9.6 $z0 11.0 9.9 ($z0 + 0.22) "ridge"
    }
}

function Build-ThunderGlaive {
    param($M, $B)
    Add-Cube $M $B 7.1 0.0 7.1 8.9 0.8 8.9 "pommel"
    Add-Rod $M $B 7.7 8.3 7.7 8.3 0.8 13.6 0.7 "shaft"
    Add-TaperBlade $M $B 8.0 8.0 11.8 15.8 10 2.2 0.3 1.0 0.0 "tip"
    for ($i = 0; $i -lt 8; $i++) {
        $t = $i / 7.0
        $y0 = 11.0 + 2.4 * $t
        $y1 = $y0 + 0.4
        $span = 3.6 * $t
        Add-Cube $M $B (7.4 - $span) $y0 7.2 (7.8 - $span * 0.7) $y1 8.8 "wing_l"
        Add-Cube $M $B (8.2 + $span * 0.7) $y0 7.2 (8.6 + $span) $y1 8.8 "wing_r"
    }
    Add-Cube $M $B 6.4 10.5 7.1 9.6 11.2 8.9 "hub"
}

function Build-WardenKatana {
    param($M, $B)
    Add-Cube $M $B 7.1 0.0 7.1 8.9 0.8 8.9 "pommel"
    Add-Rod $M $B 7.45 8.55 7.35 8.65 0.8 5.8 0.65 "grip"
    for ($i = 0; $i -lt 6; $i++) {
        $y = 1.1 + $i * 0.75
        Add-Cube $M $B 7.2 $y 7.25 8.8 ($y + 0.18) 8.75 "wrap"
    }
    Add-Cube $M $B 5.3 5.8 7.1 10.7 6.45 8.9 "guard"
    Add-TaperBlade $M $B 8.0 8.0 6.45 15.6 22 1.8 0.55 1.1 0.95 "blade"
    Add-Rod $M $B 8.32 8.45 7.05 8.95 6.8 15.2 0.9 "edge"
}

function Build-Weapon {
    param([string]$id,[string]$name)
    $modelElements = New-Object 'System.Collections.Generic.List[object]'
    $bbElements = New-Object 'System.Collections.Generic.List[object]'

    if ($id -eq 'emerald_dagger') { Build-EmeraldDagger $modelElements $bbElements }
    elseif ($id -eq 'silk_crossbow') { Build-SilkCrossbow $modelElements $bbElements }
    elseif ($id -eq 'blizzard_bow') { Build-BlizzardBow $modelElements $bbElements }
    elseif ($id -eq 'soul_scythe') { Build-SoulScythe $modelElements $bbElements }
    elseif ($id -eq 'hell_bringer') { Build-HellBringer $modelElements $bbElements }
    elseif ($id -eq 'stone_breaker') { Build-StoneBreaker $modelElements $bbElements }
    elseif ($id -eq 'thunder_glaive') { Build-ThunderGlaive $modelElements $bbElements }
    else { Build-WardenKatana $modelElements $bbElements }

    $model = [ordered]@{
        credit = "LegendaryWeaponsSMP"
        textures = [ordered]@{
            "0" = "legendary:item/$id"
            particle = "legendary:item/$id"
        }
        elements = $modelElements
        display = [ordered]@{
            thirdperson_righthand = [ordered]@{ rotation = @(0, 90, -30); translation = @(0, 2.0, -2.3); scale = @(2.0, 2.0, 2.0) }
            firstperson_righthand = [ordered]@{ rotation = @(0, -90, 24); translation = @(1.2, 3.1, 1.2); scale = @(2.35, 2.35, 2.35) }
            ground = [ordered]@{ translation = @(0, 3.0, 0); scale = @(1.35, 1.35, 1.35) }
            fixed = [ordered]@{ rotation = @(0, 180, 0); translation = @(0, 0, 0); scale = @(1.5, 1.5, 1.5) }
            gui = [ordered]@{ rotation = @(30, 225, 0); translation = @(0, 0, 0); scale = @(1.2, 1.2, 1.2) }
        }
    }

    $children = @()
    foreach ($e in $bbElements) { $children += $e.uuid }
    $bb = [ordered]@{
        meta = [ordered]@{
            format_version = "4.9"
            model_format = "free"
            box_uv = $false
        }
        name = $name
        resolution = [ordered]@{ width = 64; height = 64 }
        elements = $bbElements
        outliner = @(
            [ordered]@{
                name = "root"
                origin = @(8, 8, 8)
                children = $children
            }
        )
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $modelPath = Join-Path $modelRoot "$id.json"
    $bbPath = Join-Path $bbRoot "$id.bbmodel"
    [System.IO.File]::WriteAllText($modelPath, ($model | ConvertTo-Json -Depth 30), $utf8NoBom)
    [System.IO.File]::WriteAllText($bbPath, ($bb | ConvertTo-Json -Depth 30), $utf8NoBom)
}

$weapons = @(
    @{id='emerald_dagger';name='Emerald Dagger'},
    @{id='silk_crossbow';name='Silk Crossbow'},
    @{id='blizzard_bow';name='Blizzard Bow'},
    @{id='soul_scythe';name='Soul Scythe'},
    @{id='hell_bringer';name='Hell Bringer'},
    @{id='stone_breaker';name='Stone Breaker'},
    @{id='thunder_glaive';name='Thunder Glaive'},
    @{id='warden_katana';name='Warden Katana'}
)

foreach ($w in $weapons) {
    Build-Weapon -id $w.id -name $w.name
}
