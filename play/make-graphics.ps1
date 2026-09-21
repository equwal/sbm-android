# Draws the store graphics: the 512 px icon and the 1024x500 feature graphic.
# Usage: powershell -File play/make-graphics.ps1 -Out fastlane/metadata/android/en-US/images
param([string]$Out)
Add-Type -AssemblyName System.Drawing

$blue = [System.Drawing.Color]::FromArgb(255, 0, 0x55, 0x77)

# The ribbon of the launcher icon (M40,30 L68,30 L68,80 L54,69 L40,80 Z),
# with its centre at (cx, cy) and h pixels high.
function Ribbon($g, $cx, $cy, $h) {
    $s = $h / 50
    $pts = @(@(-14, -25), @(14, -25), @(14, 25), @(0, 14), @(-14, 25)) | ForEach-Object {
        New-Object System.Drawing.PointF ([single]($cx + $_[0] * $s)), ([single]($cy + $_[1] * $s))
    }
    $g.FillPolygon([System.Drawing.Brushes]::White, [System.Drawing.PointF[]]$pts)
}

function Canvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($blue)
    return $bmp, $g
}

# Icon: Google Play rounds the corners itself, so the square is full.
$bmp, $g = Canvas 512 512
Ribbon $g 256 256 300
$bmp.Save((Join-Path $Out "icon.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# Feature graphic.
$bmp, $g = Canvas 1024 500
Ribbon $g 190 250 250
$big = New-Object System.Drawing.Font "Segoe UI Semibold", 96
$small = New-Object System.Drawing.Font "Segoe UI", 34
$g.DrawString("sbm", $big, [System.Drawing.Brushes]::White, 330, 110)
$g.DrawString("Your bookmarks, the same", $small, [System.Drawing.Brushes]::White, 340, 270)
$g.DrawString("on each of your devices", $small, [System.Drawing.Brushes]::White, 340, 320)
$bmp.Save((Join-Path $Out "featureGraphic.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
