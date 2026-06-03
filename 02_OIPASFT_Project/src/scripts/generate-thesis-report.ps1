param(
    [string]$Source = "",
    [string]$Output = "",
    [string]$Annotations = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Source)) {
    $Source = Join-Path $repoRoot "TestLogs"
}
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $Source "Reports"
}
if ([string]::IsNullOrWhiteSpace($Annotations)) {
    $Annotations = Join-Path $Source "evaluation_annotations.csv"
}

function Get-JsonValue {
    param(
        $Object,
        [Parameter(Mandatory = $true)] [string]$Name,
        $Default = $null
    )

    if ($null -eq $Object) {
        return $Default
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $Default
    }
    return $property.Value
}

function Get-Number {
    param($Value)

    if ($null -eq $Value) {
        return [double]::NaN
    }
    try {
        $number = [double]$Value
        if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) {
            return [double]::NaN
        }
        return $number
    } catch {
        return [double]::NaN
    }
}

function Get-Bool {
    param($Value, [bool]$Default = $false)

    if ($null -eq $Value) {
        return $Default
    }
    if ($Value -is [bool]) {
        return $Value
    }
    $text = "$Value".Trim()
    if ($text -eq "") {
        return $Default
    }
    if ($text -ieq "true" -or $text -eq "1") {
        return $true
    }
    if ($text -ieq "false" -or $text -eq "0") {
        return $false
    }
    return $Default
}

function Format-Number {
    param([double]$Value, [int]$Digits = 4)

    if ([double]::IsNaN($Value) -or [double]::IsInfinity($Value)) {
        return ""
    }
    return $Value.ToString("F$Digits", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Escape-Xml {
    param([string]$Value)

    if ($null -eq $Value) {
        return ""
    }
    return [System.Security.SecurityElement]::Escape($Value)
}

function Get-ModeCountText {
    param($ModeCounts)

    if ($null -eq $ModeCounts) {
        return ""
    }
    $parts = @()
    foreach ($property in $ModeCounts.PSObject.Properties) {
        $parts += "$($property.Name)=$($property.Value)"
    }
    return ($parts -join ";")
}

function Get-AnnotationKey {
    param([string]$SessionId, $SampleIndex)

    return "$SessionId|$SampleIndex"
}

function Load-EvaluationAnnotations {
    param([string]$Path)

    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }

    $rows = @(Import-Csv -LiteralPath $Path)
    foreach ($row in $rows) {
        $sessionId = "$($row.sessionId)".Trim()
        $sampleIndex = "$($row.sampleIndex)".Trim()
        if ([string]::IsNullOrWhiteSpace($sessionId) -or [string]::IsNullOrWhiteSpace($sampleIndex)) {
            continue
        }

        $truthX = Get-Number $row.groundTruthXMeters
        $truthY = Get-Number $row.groundTruthYMeters
        if ([double]::IsNaN($truthX) -or [double]::IsNaN($truthY)) {
            continue
        }

        $truthSource = "$($row.truthSource)".Trim()
        if ([string]::IsNullOrWhiteSpace($truthSource)) {
            $truthSource = "EXTERNAL_ANNOTATION"
        }

        $map[(Get-AnnotationKey $sessionId $sampleIndex)] = [pscustomobject]@{
            groundTruthXMeters = $truthX
            groundTruthYMeters = $truthY
            truthSource = $truthSource
            evaluationEligible = Get-Bool $row.evaluationEligible $true
        }
    }

    return $map
}

function Write-PathSvg {
    param(
        [Parameter(Mandatory = $true)] [string]$Path,
        [Parameter(Mandatory = $true)] [string]$SessionId,
        [Parameter(Mandatory = $true)] $SessionRows,
        [double]$RoomWidthMeters,
        [double]$RoomHeightMeters,
        $Beacons
    )

    if ($SessionRows.Count -eq 0) {
        return
    }

    if ([double]::IsNaN($RoomWidthMeters) -or $RoomWidthMeters -le 0.0) {
        $maxX = ($SessionRows | ForEach-Object { Get-Number $_.renderXMeters } | Measure-Object -Maximum).Maximum
        $RoomWidthMeters = [Math]::Max(1.0, (Get-Number $maxX) + 0.5)
    }
    if ([double]::IsNaN($RoomHeightMeters) -or $RoomHeightMeters -le 0.0) {
        $maxY = ($SessionRows | ForEach-Object { Get-Number $_.renderYMeters } | Measure-Object -Maximum).Maximum
        $RoomHeightMeters = [Math]::Max(1.0, (Get-Number $maxY) + 0.5)
    }

    $width = 900
    $height = 650
    $pad = 70
    $plotW = $width - (2 * $pad)
    $plotH = $height - (2 * $pad)

    function X-To-Px([double]$x) {
        return $pad + (($x / $RoomWidthMeters) * $plotW)
    }
    function Y-To-Px([double]$y) {
        return $pad + (($y / $RoomHeightMeters) * $plotH)
    }

    $points = @()
    foreach ($row in $SessionRows) {
        $x = Get-Number $row.renderXMeters
        $y = Get-Number $row.renderYMeters
        if (-not [double]::IsNaN($x) -and -not [double]::IsNaN($y)) {
            $points += ("{0},{1}" -f (Format-Number (X-To-Px $x) 2), (Format-Number (Y-To-Px $y) 2))
        }
    }

    $svg = New-Object System.Collections.Generic.List[string]
    $svg.Add("<svg xmlns=""http://www.w3.org/2000/svg"" width=""$width"" height=""$height"" viewBox=""0 0 $width $height"">")
    $svg.Add("<rect width=""100%"" height=""100%"" fill=""#f6f8fb""/>")
    $svg.Add("<text x=""$pad"" y=""36"" font-family=""Arial"" font-size=""22"" font-weight=""700"" fill=""#1c232d"">Path trace: $(Escape-Xml $SessionId)</text>")
    $svg.Add("<rect x=""$pad"" y=""$pad"" width=""$plotW"" height=""$plotH"" fill=""#ffffff"" stroke=""#c9d3df"" stroke-width=""2""/>")

    for ($i = 0; $i -le 4; $i++) {
        $gx = $pad + (($plotW / 4.0) * $i)
        $gy = $pad + (($plotH / 4.0) * $i)
        $svg.Add("<line x1=""$(Format-Number $gx 2)"" y1=""$pad"" x2=""$(Format-Number $gx 2)"" y2=""$($pad + $plotH)"" stroke=""#e8edf4""/>")
        $svg.Add("<line x1=""$pad"" y1=""$(Format-Number $gy 2)"" x2=""$($pad + $plotW)"" y2=""$(Format-Number $gy 2)"" stroke=""#e8edf4""/>")
    }

    if ($points.Count -gt 1) {
        $svg.Add("<polyline points=""$($points -join ' ')"" fill=""none"" stroke=""#1c846f"" stroke-width=""4"" stroke-linejoin=""round"" stroke-linecap=""round""/>")
    }

    foreach ($row in $SessionRows) {
        $x = Get-Number $row.renderXMeters
        $y = Get-Number $row.renderYMeters
        if ([double]::IsNaN($x) -or [double]::IsNaN($y)) {
            continue
        }
        $px = X-To-Px $x
        $py = Y-To-Px $y
        $fill = if ($row.mode -eq "ANCHOR" -or $row.mode -eq "MANUAL_ANCHOR") { "#6b4bb8" } elseif ($row.hasBleFix -eq "True") { "#de962d" } else { "#1c846f" }
        $svg.Add("<circle cx=""$(Format-Number $px 2)"" cy=""$(Format-Number $py 2)"" r=""6"" fill=""$fill""/>")
    }

    foreach ($row in $SessionRows) {
        $truthX = Get-Number $row.groundTruthXMeters
        $truthY = Get-Number $row.groundTruthYMeters
        if ([double]::IsNaN($truthX) -or [double]::IsNaN($truthY)) {
            continue
        }
        $px = X-To-Px $truthX
        $py = Y-To-Px $truthY
        $svg.Add("<line x1=""$(Format-Number ($px - 10) 2)"" y1=""$(Format-Number $py 2)"" x2=""$(Format-Number ($px + 10) 2)"" y2=""$(Format-Number $py 2)"" stroke=""#6b4bb8"" stroke-width=""3""/>")
        $svg.Add("<line x1=""$(Format-Number $px 2)"" y1=""$(Format-Number ($py - 10) 2)"" x2=""$(Format-Number $px 2)"" y2=""$(Format-Number ($py + 10) 2)"" stroke=""#6b4bb8"" stroke-width=""3""/>")
    }

    if ($null -ne $Beacons) {
        foreach ($beacon in $Beacons) {
            $bx = Get-Number (Get-JsonValue $beacon "xMeters")
            $by = Get-Number (Get-JsonValue $beacon "yMeters")
            if ([double]::IsNaN($bx) -or [double]::IsNaN($by)) {
                continue
            }
            $px = X-To-Px $bx
            $py = Y-To-Px $by
            $label = Escape-Xml (Get-JsonValue $beacon "id" "")
            $svg.Add("<circle cx=""$(Format-Number $px 2)"" cy=""$(Format-Number $py 2)"" r=""8"" fill=""#3768aa"" stroke=""#ffffff"" stroke-width=""3""/>")
            $svg.Add("<text x=""$(Format-Number ($px + 12) 2)"" y=""$(Format-Number ($py - 10) 2)"" font-family=""Arial"" font-size=""13"" fill=""#1c232d"">$label</text>")
        }
    }

    $svg.Add("<text x=""$pad"" y=""$($height - 24)"" font-family=""Arial"" font-size=""13"" fill=""#5f6a78"">green=render/fused path, orange=BLE fix, purple=anchor, cross=ground truth when present</text>")
    $svg.Add("</svg>")
    Set-Content -LiteralPath $Path -Value $svg -Encoding UTF8
}

function Write-ErrorSvg {
    param(
        [Parameter(Mandatory = $true)] [string]$Path,
        [Parameter(Mandatory = $true)] [string]$SessionId,
        [Parameter(Mandatory = $true)] $SessionRows
    )

    $errorRows = @($SessionRows | Where-Object {
        (Get-Bool $_.isEvaluationSample $false) -and -not [double]::IsNaN((Get-Number $_.errorMeters))
    })
    if ($errorRows.Count -eq 0) {
        return
    }

    $width = 900
    $height = 420
    $pad = 60
    $plotW = $width - (2 * $pad)
    $plotH = $height - (2 * $pad)
    $maxElapsed = ($errorRows | ForEach-Object { Get-Number $_.elapsedSeconds } | Measure-Object -Maximum).Maximum
    $maxError = ($errorRows | ForEach-Object { Get-Number $_.errorMeters } | Measure-Object -Maximum).Maximum
    $maxElapsed = [Math]::Max(1.0, (Get-Number $maxElapsed))
    $maxError = [Math]::Max(0.25, (Get-Number $maxError))

    $points = @()
    foreach ($row in $errorRows) {
        $x = Get-Number $row.elapsedSeconds
        $e = Get-Number $row.errorMeters
        $px = $pad + (($x / $maxElapsed) * $plotW)
        $py = $pad + $plotH - (($e / $maxError) * $plotH)
        $points += ("{0},{1}" -f (Format-Number $px 2), (Format-Number $py 2))
    }

    $svg = New-Object System.Collections.Generic.List[string]
    $svg.Add("<svg xmlns=""http://www.w3.org/2000/svg"" width=""$width"" height=""$height"" viewBox=""0 0 $width $height"">")
    $svg.Add("<rect width=""100%"" height=""100%"" fill=""#f6f8fb""/>")
    $svg.Add("<text x=""$pad"" y=""34"" font-family=""Arial"" font-size=""22"" font-weight=""700"" fill=""#1c232d"">Position error: $(Escape-Xml $SessionId)</text>")
    $svg.Add("<rect x=""$pad"" y=""$pad"" width=""$plotW"" height=""$plotH"" fill=""#ffffff"" stroke=""#c9d3df"" stroke-width=""2""/>")
    $svg.Add("<polyline points=""$($points -join ' ')"" fill=""none"" stroke=""#b03737"" stroke-width=""4"" stroke-linejoin=""round"" stroke-linecap=""round""/>")
    $svg.Add("<text x=""$pad"" y=""$($height - 20)"" font-family=""Arial"" font-size=""13"" fill=""#5f6a78"">x=time in seconds, y=error in meters; max error shown=$(Format-Number $maxError 2)m</text>")
    $svg.Add("</svg>")
    Set-Content -LiteralPath $Path -Value $svg -Encoding UTF8
}

if (-not (Test-Path -LiteralPath $Source)) {
    throw "Log source folder not found: $Source"
}

New-Item -ItemType Directory -Path $Output -Force | Out-Null

$sessionRows = New-Object System.Collections.Generic.List[object]
$sampleRows = New-Object System.Collections.Generic.List[object]
$annotationMap = Load-EvaluationAnnotations -Path $Annotations
if ($annotationMap.Count -gt 0) {
    Write-Host "Loaded evaluation annotations: $($annotationMap.Count) from $Annotations"
}
$jsonFiles = @(Get-ChildItem -LiteralPath $Source -Filter "*_user_position_history.json" -File | Sort-Object Name)
if ($jsonFiles.Count -eq 0) {
    throw "No *_user_position_history.json files found in $Source. Pull logs from the device before generating thesis reports."
}

foreach ($file in $jsonFiles) {
    $root = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    $session = Get-JsonValue $root "session"
    $summary = Get-JsonValue $root "summary"
    $algorithm = Get-JsonValue (Get-JsonValue $root "experiment") "algorithm"
    $room = Get-JsonValue $root "room"
    $positions = @(Get-JsonValue $root "positions" @())
    $sessionId = Get-JsonValue $session "sessionId" (Get-JsonValue $root "sessionId" $file.BaseName.Replace("_user_position_history", ""))
    $firstTimestamp = if ($positions.Count -gt 0) { Get-Number (Get-JsonValue $positions[0] "timestampMs") } else { [double]::NaN }

    $bleFixSamples = 0
    $groundTruthSamples = 0
    $evaluationSamples = 0
    $bleEvaluationSamples = 0
    $bleRawEvaluationSamples = 0
    $adaptiveSamples = 0
    $errorSum = 0.0
    $squaredErrorSum = 0.0
    $maxError = 0.0
    $bleErrorSum = 0.0
    $bleSquaredErrorSum = 0.0
    $bleMaxError = 0.0
    $bleRawErrorSum = 0.0
    $bleRawSquaredErrorSum = 0.0
    $bleRawMaxError = 0.0

    foreach ($position in $positions) {
        $sampleIndex = Get-JsonValue $position "sampleIndex" ""
        $mode = Get-JsonValue $position "mode" ""
        $timestamp = Get-Number (Get-JsonValue $position "timestampMs")
        $elapsedMs = Get-Number (Get-JsonValue $position "elapsedMs")
        if ([double]::IsNaN($elapsedMs) -and -not [double]::IsNaN($timestamp) -and -not [double]::IsNaN($firstTimestamp)) {
            $elapsedMs = $timestamp - $firstTimestamp
        }

        $renderX = Get-Number (Get-JsonValue $position "renderXMeters")
        $renderY = Get-Number (Get-JsonValue $position "renderYMeters")
        $bleX = Get-Number (Get-JsonValue $position "bleXMeters")
        $bleY = Get-Number (Get-JsonValue $position "bleYMeters")
        $truthX = Get-Number (Get-JsonValue $position "groundTruthXMeters")
        $truthY = Get-Number (Get-JsonValue $position "groundTruthYMeters")
        $truthSource = Get-JsonValue $position "truthSource" ""
        $eligibleValue = Get-JsonValue $position "evaluationEligible" $null
        $evaluationEligible = if ($null -ne $eligibleValue) {
            Get-Bool $eligibleValue $false
        } else {
            $false
        }
        $annotation = $annotationMap[(Get-AnnotationKey $sessionId $sampleIndex)]
        if ($null -ne $annotation) {
            $truthX = $annotation.groundTruthXMeters
            $truthY = $annotation.groundTruthYMeters
            $truthSource = $annotation.truthSource
            $evaluationEligible = $annotation.evaluationEligible
        }
        $positionError = Get-Number (Get-JsonValue $position "errorMeters")
        if (-not [double]::IsNaN($truthX) -and -not [double]::IsNaN($truthY)) {
            $positionError = [Math]::Sqrt([Math]::Pow($renderX - $truthX, 2.0) + [Math]::Pow($renderY - $truthY, 2.0))
        }
        $hasBleFix = -not [double]::IsNaN($bleX) -and -not [double]::IsNaN($bleY)
        $adaptiveEnabled = Get-Bool (Get-JsonValue $position "adaptiveTuningEnabled" $false) $false
        if ($adaptiveEnabled) {
            $adaptiveSamples++
        }
        $bleRawError = Get-Number (Get-JsonValue $position "bleErrorMeters")
        if ([double]::IsNaN($bleRawError) -and $hasBleFix -and -not [double]::IsNaN($truthX) -and -not [double]::IsNaN($truthY)) {
            $bleRawError = [Math]::Sqrt([Math]::Pow($bleX - $truthX, 2.0) + [Math]::Pow($bleY - $truthY, 2.0))
        }
        $isFreshBleFix = $hasBleFix -and ($mode -eq "BLE_ONLY" -or $mode -eq "BLE_PLUS_FUSION" -or $mode -eq "FUSION")
        if ($isFreshBleFix) {
            $bleFixSamples++
        }
        if (-not [double]::IsNaN($positionError)) {
            $groundTruthSamples++
            if ($evaluationEligible) {
                $evaluationSamples++
                $errorSum += $positionError
                $squaredErrorSum += $positionError * $positionError
                $maxError = [Math]::Max($maxError, $positionError)
                if ($isFreshBleFix) {
                    $bleEvaluationSamples++
                    $bleErrorSum += $positionError
                    $bleSquaredErrorSum += $positionError * $positionError
                    $bleMaxError = [Math]::Max($bleMaxError, $positionError)
                }
            }
        }
        if (-not [double]::IsNaN($bleRawError) -and $isFreshBleFix -and $evaluationEligible) {
            $bleRawEvaluationSamples++
            $bleRawErrorSum += $bleRawError
            $bleRawSquaredErrorSum += $bleRawError * $bleRawError
            $bleRawMaxError = [Math]::Max($bleRawMaxError, $bleRawError)
        }

        $sampleRows.Add([pscustomobject]@{
            sessionId = $sessionId
            sampleIndex = $sampleIndex
            timestampMs = Format-Number $timestamp 0
            elapsedSeconds = Format-Number ($elapsedMs / 1000.0) 3
            mode = $mode
            reason = Get-JsonValue $position "reason" ""
            renderXMeters = Format-Number $renderX 4
            renderYMeters = Format-Number $renderY 4
            bleXMeters = Format-Number $bleX 4
            bleYMeters = Format-Number $bleY 4
            hasBleFix = $hasBleFix
            truthSource = $truthSource
            isEvaluationSample = (-not [double]::IsNaN($positionError) -and $evaluationEligible)
            isBleEvaluationSample = (-not [double]::IsNaN($positionError) -and $evaluationEligible -and $isFreshBleFix)
            groundTruthXMeters = Format-Number $truthX 4
            groundTruthYMeters = Format-Number $truthY 4
            errorMeters = Format-Number $positionError 4
            bleRawErrorMeters = Format-Number $bleRawError 4
            liveCount = Get-JsonValue $position "liveCount" ""
            filteredCount = Get-JsonValue $position "filteredCount" ""
            adaptiveTuningEnabled = $adaptiveEnabled
            bleQualityScore = Format-Number (Get-Number (Get-JsonValue $position "bleQualityScore")) 4
            pdrQualityScore = Format-Number (Get-Number (Get-JsonValue $position "pdrQualityScore")) 4
            rssiNoiseDb = Format-Number (Get-Number (Get-JsonValue $position "rssiNoiseDb")) 4
            bleMeasurementStdMeters = Format-Number (Get-Number (Get-JsonValue $position "bleMeasurementStdMeters")) 4
            bleResidualRmsMeters = Format-Number (Get-Number (Get-JsonValue $position "bleResidualRmsMeters")) 4
            bleResidualMaxMeters = Format-Number (Get-Number (Get-JsonValue $position "bleResidualMaxMeters")) 4
            motionState = Get-JsonValue $position "motionState" ""
            recordingEnabled = Get-Bool (Get-JsonValue $position "recordingEnabled" $true) $true
            trialLabel = Get-JsonValue $position "trialLabel" ""
        })
    }

    $sampleCount = $positions.Count
    $meanError = if ($evaluationSamples -gt 0) { $errorSum / $evaluationSamples } else { [double]::NaN }
    $rmse = if ($evaluationSamples -gt 0) { [Math]::Sqrt($squaredErrorSum / $evaluationSamples) } else { [double]::NaN }
    $bleMeanError = if ($bleEvaluationSamples -gt 0) { $bleErrorSum / $bleEvaluationSamples } else { [double]::NaN }
    $bleRmse = if ($bleEvaluationSamples -gt 0) { [Math]::Sqrt($bleSquaredErrorSum / $bleEvaluationSamples) } else { [double]::NaN }
    $bleRawMeanError = if ($bleRawEvaluationSamples -gt 0) { $bleRawErrorSum / $bleRawEvaluationSamples } else { [double]::NaN }
    $bleRawRmse = if ($bleRawEvaluationSamples -gt 0) { [Math]::Sqrt($bleRawSquaredErrorSum / $bleRawEvaluationSamples) } else { [double]::NaN }

    $sessionRows.Add([pscustomobject]@{
        sessionId = $sessionId
        sourceFile = $file.Name
        sampleCount = $sampleCount
        bleFixSamples = $bleFixSamples
        bleFixRatio = Format-Number ($(if ($sampleCount -gt 0) { $bleFixSamples / $sampleCount } else { [double]::NaN })) 4
        groundTruthSamples = $groundTruthSamples
        adaptiveSamples = $adaptiveSamples
        evaluationSamples = $evaluationSamples
        bleEvaluationSamples = $bleEvaluationSamples
        bleRawEvaluationSamples = $bleRawEvaluationSamples
        meanErrorMeters = Format-Number $meanError 4
        rmseMeters = Format-Number $rmse 4
        maxErrorMeters = Format-Number ($(if ($evaluationSamples -gt 0) { $maxError } else { [double]::NaN })) 4
        bleMeanErrorMeters = Format-Number $bleMeanError 4
        bleRmseMeters = Format-Number $bleRmse 4
        bleMaxErrorMeters = Format-Number ($(if ($bleEvaluationSamples -gt 0) { $bleMaxError } else { [double]::NaN })) 4
        bleRawMeanErrorMeters = Format-Number $bleRawMeanError 4
        bleRawRmseMeters = Format-Number $bleRawRmse 4
        bleRawMaxErrorMeters = Format-Number ($(if ($bleRawEvaluationSamples -gt 0) { $bleRawMaxError } else { [double]::NaN })) 4
        rssiAverageWindowSize = Get-JsonValue $algorithm "rssiAverageWindowSize" ""
        pathLossExponent = Get-JsonValue $algorithm "pathLossExponent" ""
        modeCounts = Get-ModeCountText (Get-JsonValue $summary "modeCounts")
    })

    $sessionSamples = @($sampleRows | Where-Object { $_.sessionId -eq $sessionId })
    $roomWidth = Get-Number (Get-JsonValue $room "widthMeters")
    $roomHeight = Get-Number (Get-JsonValue $room "heightMeters")
    $beacons = Get-JsonValue $root "beacons" @()
    Write-PathSvg -Path (Join-Path $Output "$sessionId`_path.svg") -SessionId $sessionId -SessionRows $sessionSamples -RoomWidthMeters $roomWidth -RoomHeightMeters $roomHeight -Beacons $beacons
    Write-ErrorSvg -Path (Join-Path $Output "$sessionId`_error.svg") -SessionId $sessionId -SessionRows $sessionSamples
}

$summaryCsv = Join-Path $Output "thesis_sessions_summary.csv"
$samplesCsv = Join-Path $Output "thesis_samples.csv"
$sessionRows | Export-Csv -LiteralPath $summaryCsv -NoTypeInformation -Encoding UTF8
$sampleRows | Export-Csv -LiteralPath $samplesCsv -NoTypeInformation -Encoding UTF8

Write-Host "Generated thesis report files:"
Write-Host "  $summaryCsv"
Write-Host "  $samplesCsv"
Write-Host "  SVG charts in $Output"
