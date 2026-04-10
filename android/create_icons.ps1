$base64 = "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAOxAAADsQBlSsOGwAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAABSSURBVGiB7c0BDQAgDABBaP8nG9IBG5KBPJQL5SL5gFzEC+UiXigX8UK5iBfKRbxQLuKFchEvlIt4oVwkF8lFcpFcJBfJRXKRXOQPcD7w8wZxL0oAAAAASUVORK5CYII="
$bytes = [Convert]::FromBase64String($base64)

$dirs = @(
    "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-mdpi",
    "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-hdpi",
    "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-xhdpi",
    "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-xxhdpi",
    "d:\cursor\Vitbon_kassa_08.04.2026\android\app\src\main\res\mipmap-xxxhdpi"
)

foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [System.IO.File]::WriteAllBytes("$dir\ic_launcher.png", $bytes)
    [System.IO.File]::WriteAllBytes("$dir\ic_launcher_round.png", $bytes)
}

Write-Host "Done"
