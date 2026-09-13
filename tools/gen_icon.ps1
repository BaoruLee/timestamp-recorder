# Generate app launcher icons: blue background + white clock face.
# Output: mipmap-mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192
# Uses embedded C# with System.Drawing (built into Windows). No extra installs.
# NOTE: keep this file pure ASCII (PowerShell 5.1 reads scripts as ANSI).
$ErrorActionPreference = "Stop"

$code = @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class IconGen
{
    public static void Save(string path, int size)
    {
        int s = size * 4; // 4x supersampling
        var bmp = new Bitmap(s, s, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);

            // solid background #1565C0 (square icon; launcher applies mask)
            using (var b = new SolidBrush(Color.FromArgb(255, 21, 101, 192)))
                g.FillRectangle(b, 0, 0, s, s);

            // white clock ring
            float cx = s / 2f, cy = s / 2f;
            float ringR = s * 0.315f, ringW = Math.Max(1f, s * 0.055f);
            using (var pen = new Pen(Color.White, ringW) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                g.DrawEllipse(pen, cx - ringR, cy - ringR, 2 * ringR, 2 * ringR);

            // hour hand at 10, minute hand at 2 (classic 10:10)
            DrawHand(g, cx, cy, s, 300, 0.175, 0.070);
            DrawHand(g, cx, cy, s, 60, 0.255, 0.055);

            // center dot
            float dotR = Math.Max(1f, s * 0.045f);
            g.FillEllipse(Brushes.White, cx - dotR, cy - dotR, 2 * dotR, 2 * dotR);
        }

        // downscale to target size
        using (var final = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (var g2 = Graphics.FromImage(final))
        {
            g2.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g2.SmoothingMode = SmoothingMode.HighQuality;
            g2.DrawImage(bmp, 0, 0, size, size);
            final.Save(path, ImageFormat.Png);
        }
        bmp.Dispose();
    }

    private static void DrawHand(Graphics g, float cx, float cy, int s, double deg, double lenRatio, double widthRatio)
    {
        double rad = deg * Math.PI / 180.0;
        float ex = (float)(cx + Math.Sin(rad) * s * lenRatio);
        float ey = (float)(cy - Math.Cos(rad) * s * lenRatio);
        using (var pen = new Pen(Color.White, (float)Math.Max(1.0, s * widthRatio)) { StartCap = LineCap.Round, EndCap = LineCap.Round })
            g.DrawLine(pen, cx, cy, ex, ey);
    }
}
"@

Add-Type -TypeDefinition $code -ReferencedAssemblies @("System.Drawing.dll", "System.Core.dll")

$base = Join-Path $PSScriptRoot "..\app\src\main\res"
$densities = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($folder in $densities.Keys) {
    $outDir = Join-Path $base $folder
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    [IconGen]::Save((Join-Path $outDir "ic_launcher.png"), $densities[$folder])
    Write-Host "$folder : $($densities[$folder])x$($densities[$folder]) ok"
}
Write-Host "icons generated"
