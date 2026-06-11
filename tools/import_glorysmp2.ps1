$ErrorActionPreference = 'Stop'

$citRoot = Join-Path $PSScriptRoot "..\\GlorySmp2-v1.0\\Glory smp\\assets\\minecraft\\optifine\\cit"
$modelOut = Join-Path $PSScriptRoot "..\\src\\main\\resources\\rp\\models\\item"
$texOutRoot = Join-Path $PSScriptRoot "..\\src\\main\\resources\\rp\\textures\\item"

$mapping = [ordered]@{
    emerald_dagger = "b\\sword.json"
    silk_crossbow = "c\\bow.json"
    blizzard_bow = "c\\bow.json"
    soul_scythe = "a\\greatsword.json"
    hell_bringer = "d\\20.json"
    stone_breaker = "god\\thor_hammer_shiny.json"
    thunder_glaive = "a\\greatsword.json"
    warden_katana = "b\\sword.json"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function IsEffectTextureToken([string]$token) {
    return $token -match '(?i)(^|[\/_\-])(anim|animation|spark|fx|glow|trail|aura|beam|shine|shiny|enchant|emissive)'
}

foreach ($entry in $mapping.GetEnumerator()) {
    $weapon = $entry.Key
    $rel = $entry.Value
    $modelPath = Join-Path $citRoot $rel
    if (-not (Test-Path $modelPath)) {
        throw "Missing model: $modelPath"
    }

    $model = (Get-Content $modelPath -Raw) | ConvertFrom-Json
    $sourceDir = Split-Path $modelPath -Parent
    $dstDir = Join-Path $texOutRoot $weapon

    if (Test-Path $dstDir) {
        Remove-Item $dstDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dstDir -Force | Out-Null

    $copiedFiles = New-Object 'System.Collections.Generic.List[string]'

    $textureEntries = New-Object 'System.Collections.Generic.List[object]'
    if ($null -ne $model.textures) {
        foreach ($prop in $model.textures.PSObject.Properties) {
            $v = [string]$prop.Value
            if ($v.StartsWith("#")) {
                continue
            }
            if ($v -match '^[^:]+:(.+)$') {
                $v = $matches[1]
            }
            $v = $v -replace '^\./', ''
            $v = $v -replace '^\.\\', ''
            $v = $v -replace '\.png$', ''
            $v = $v.Trim('/').Trim()
            if ([string]::IsNullOrWhiteSpace($v)) {
                $v = "layer0"
            }
            $prop.Value = "legendary:item/$weapon/$v"
            [void]$textureEntries.Add([pscustomobject]@{
                Key = $prop.Name
                Token = $v
                IsEffect = (IsEffectTextureToken $v)
            })
        }
    }

    $allowedKeys = @()
    foreach ($t in $textureEntries) {
        if ($t.Key -eq "particle") {
            continue
        }
        if (-not $t.IsEffect) {
            $allowedKeys += [string]$t.Key
        }
    }
    if ($allowedKeys.Count -eq 0) {
        foreach ($t in $textureEntries) {
            if ($t.Key -ne "particle") {
                $allowedKeys += [string]$t.Key
            }
        }
    }
    if ($allowedKeys.Count -eq 0) {
        throw "No textures found for $weapon in $modelPath"
    }
    $primaryKey = $allowedKeys[0]
    $allowedSet = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($k in $allowedKeys) {
        [void]$allowedSet.Add($k)
    }

    $resolvedDstDir = (Resolve-Path $dstDir).Path
    foreach ($t in $textureEntries) {
        if (-not $allowedSet.Contains([string]$t.Key)) {
            continue
        }
        $v = [string]$t.Token
        $srcPng = Join-Path $sourceDir ($v + ".png")
        if (-not (Test-Path $srcPng)) {
            $alt = Join-Path $sourceDir ($v -replace '/', '\')
            if (Test-Path ($alt + ".png")) {
                $srcPng = $alt + ".png"
            } else {
                throw "Missing texture for ${weapon}: ${v} from $modelPath"
            }
        }
        $dstPng = Join-Path $dstDir (($v -replace '/', '\') + ".png")
        $dstParent = Split-Path $dstPng -Parent
        New-Item -ItemType Directory -Path $dstParent -Force | Out-Null
        Copy-Item $srcPng $dstPng -Force
        $relPng = (Resolve-Path $dstPng).Path.Substring($resolvedDstDir.Length + 1).Replace('\', '/')
        if (-not $copiedFiles.Contains($relPng)) {
            [void]$copiedFiles.Add($relPng)
        }
        $srcMeta = $srcPng + ".mcmeta"
        if (Test-Path $srcMeta) {
            $dstMeta = $dstPng + ".mcmeta"
            Copy-Item $srcMeta $dstMeta -Force
            $relMeta = (Resolve-Path $dstMeta).Path.Substring($resolvedDstDir.Length + 1).Replace('\', '/')
            if (-not $copiedFiles.Contains($relMeta)) {
                [void]$copiedFiles.Add($relMeta)
            }
        }
    }

    $filteredElements = New-Object 'System.Collections.Generic.List[object]'
    if ($null -ne $model.elements) {
        foreach ($el in $model.elements) {
            if ($null -eq $el.from -or $null -eq $el.to -or $null -eq $el.faces) {
                continue
            }
            $dx = [math]::Abs([double]$el.to[0] - [double]$el.from[0])
            $dy = [math]::Abs([double]$el.to[1] - [double]$el.from[1])
            $dz = [math]::Abs([double]$el.to[2] - [double]$el.from[2])
            if ($dx -lt 0.1 -or $dy -lt 0.1 -or $dz -lt 0.1) {
                continue
            }
            $reject = $false
            foreach ($faceProp in $el.faces.PSObject.Properties) {
                $face = $faceProp.Value
                $faceTexture = [string]$face.texture
                if ([string]::IsNullOrWhiteSpace($faceTexture)) {
                    continue
                }
                if ($faceTexture.StartsWith("#")) {
                    $key = $faceTexture.Substring(1)
                    if (-not $allowedSet.Contains($key)) {
                        $reject = $true
                        break
                    }
                } else {
                    $face.texture = "#$primaryKey"
                }
            }
            if ($reject) {
                continue
            }
            [void]$filteredElements.Add($el)
        }
    }
    $model.elements = $filteredElements

    $newTextures = [ordered]@{}
    foreach ($k in $allowedKeys) {
        $newTextures[$k] = [string]$model.textures.$k
    }
    $newTextures["particle"] = [string]$model.textures.$primaryKey
    $model.textures = [pscustomobject]$newTextures

    $maxUv = 0.0
    foreach ($el in $model.elements) {
        foreach ($faceProp in $el.faces.PSObject.Properties) {
            $face = $faceProp.Value
            if ($null -eq $face.uv) {
                continue
            }
            $uv = @($face.uv)
            for ($i = 0; $i -lt $uv.Count; $i += 2) {
                $u = [double]$uv[$i]
                $v = [double]$uv[$i + 1]
                if ($u -gt $maxUv) {
                    $maxUv = $u
                }
                if ($v -gt $maxUv) {
                    $maxUv = $v
                }
            }
        }
    }
    $requiredSize = [math]::Max(16, [math]::Ceiling($maxUv))
    if ($null -eq $model.texture_size -or $model.texture_size.Count -lt 2) {
        $model.texture_size = @($requiredSize, $requiredSize)
    } else {
        $tw = [double]$model.texture_size[0]
        $th = [double]$model.texture_size[1]
        if ($tw -lt $requiredSize -or $th -lt $requiredSize) {
            $model.texture_size = @($requiredSize, $requiredSize)
        }
    }
    if ($null -ne $model.PSObject.Properties["groups"]) {
        [void]$model.PSObject.Properties.Remove("groups")
    }

    $modelJson = $model | ConvertTo-Json -Depth 100 -Compress
    [System.IO.File]::WriteAllText((Join-Path $modelOut ($weapon + ".json")), $modelJson, $utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $dstDir "index.txt"), ([string]::Join("`n", $copiedFiles)) + "`n", $utf8NoBom)
}
