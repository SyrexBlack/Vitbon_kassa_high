Add-Type -AssemblyName System.Drawing

$iconSpecs = @(
    @{ Dir = "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-mdpi"; Size = 48 },
    @{ Dir = "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-hdpi"; Size = 72 },
    @{ Dir = "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-xhdpi"; Size = 96 },
    @{ Dir = "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-xxhdpi"; Size = 144 },
    @{ Dir = "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-xxxhdpi"; Size = 192 }
)

$backgroundColor = [System.Drawing.Color]::FromArgb(255, 18, 82, 147)
$foregroundColor = [System.Drawing.Color]::White

function New-LauncherIcon {
    param(
        [string]$Path,
        [int]$Size,
        [bool]$Round
    )

    $bitmap = New-Object System.Drawing.Bitmap($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $brush = New-Object System.Drawing.SolidBrush($backgroundColor)
    $textBrush = New-Object System.Drawing.SolidBrush($foregroundColor)
    $fontSize = [Math]::Max([int]($Size * 0.48), 16)
    $font = New-Object System.Drawing.Font("Segoe UI", $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)

    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
        $graphics.Clear([System.Drawing.Color]::Transparent)

        if ($Round) {
            $graphics.FillEllipse($brush, 0, 0, $Size - 1, $Size - 1)
        } else {
            $graphics.FillRectangle($brush, 0, 0, $Size, $Size)
        }

        $text = "V"
        $layout = [System.Drawing.RectangleF]::new(0, 0, $Size, $Size)
        $stringFormat = [System.Drawing.StringFormat]::new()
        $stringFormat.Alignment = [System.Drawing.StringAlignment]::Center
        $stringFormat.LineAlignment = [System.Drawing.StringAlignment]::Center
        $graphics.DrawString($text, $font, $textBrush, $layout, $stringFormat)

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $font.Dispose()
        $textBrush.Dispose()
        $brush.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

foreach ($spec in $iconSpecs) {
    New-Item -ItemType Directory -Force -Path $spec.Dir | Out-Null
    New-LauncherIcon -Path (Join-Path $spec.Dir "ic_launcher.png") -Size $spec.Size -Round:$false
    New-LauncherIcon -Path (Join-Path $spec.Dir "ic_launcher_round.png") -Size $spec.Size -Round:$true
}

Write-Host "Launcher icons regenerated"
