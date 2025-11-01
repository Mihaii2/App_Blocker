param(
    [string]$Message = "Time to sleep!",
    [double]$Width = 420,
    [double]$Height = 160,
    [double]$Opacity = 0.88,
    [string]$BackgroundColor = "#CC1A1A3A",
    [string]$TextColor = "#FFFFFFFF",
    [int]$FontSize = 28,
    [int]$CornerRadius = 18,
    [string]$StartTime,
    [string]$EndTime,
    [int]$CheckIntervalSeconds = 30
)

Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase, System.Windows.Forms
$script:isClamping = $false

function Get-WindowDpi {
    $source = [System.Windows.PresentationSource]::FromVisual($window)
    if ($source -and $source.CompositionTarget) {
        $transform = $source.CompositionTarget.TransformToDevice
        return @{ ScaleX = $transform.M11; ScaleY = $transform.M22 }
    }
    return @{ ScaleX = 1.0; ScaleY = 1.0 }
}

function Set-ReminderWindowBounds {
    if ($script:isClamping) { return }
    $script:isClamping = $true
    try {
        $dpi = Get-WindowDpi
        $deviceLeft = $window.Left * $dpi.ScaleX
        $deviceTop = $window.Top * $dpi.ScaleY
        $deviceWidth = $window.Width * $dpi.ScaleX
        $deviceHeight = $window.Height * $dpi.ScaleY

        $screenPoint = New-Object System.Drawing.Point([int][Math]::Round($deviceLeft), [int][Math]::Round($deviceTop))
        $screen = [System.Windows.Forms.Screen]::FromPoint($screenPoint)
        $workArea = $screen.WorkingArea

        $availableWidth = [Math]::Max(1, $workArea.Width)
        $availableHeight = [Math]::Max(1, $workArea.Height)

        if ($deviceWidth -gt $availableWidth) {
            $deviceWidth = $availableWidth
            $window.Width = $deviceWidth / $dpi.ScaleX
        }
        if ($deviceHeight -gt $availableHeight) {
            $deviceHeight = $availableHeight
            $window.Height = $deviceHeight / $dpi.ScaleY
        }

        $minLeft = $workArea.Left / $dpi.ScaleX
        $minTop = $workArea.Top / $dpi.ScaleY
        $maxLeft = ($workArea.Right - $deviceWidth) / $dpi.ScaleX
        $maxTop = ($workArea.Bottom - $deviceHeight) / $dpi.ScaleY

        if ($maxLeft -lt $minLeft) { $maxLeft = $minLeft }
        if ($maxTop -lt $minTop) { $maxTop = $minTop }

    $window.Left = [Math]::Min([Math]::Max($window.Left, $minLeft), $maxLeft)
    $window.Top = [Math]::Min([Math]::Max($window.Top, $minTop), $maxTop)
    }
    finally {
        $script:isClamping = $false
    }
}

function Get-MinuteOfDay {
    param([string]$TimeString)

    if ([string]::IsNullOrWhiteSpace($TimeString)) {
        return $null
    }

    $styles = [System.Globalization.DateTimeStyles]::None
    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $parsed = [DateTime]::MinValue
    if (-not [DateTime]::TryParseExact($TimeString.Trim(), "HH:mm", $culture, $styles, [ref]$parsed)) {
        throw "Time value '$TimeString' must be in 24h HH:mm format (e.g. 22:30)."
    }

    return ($parsed.Hour * 60) + $parsed.Minute
}

$startMinutes = Get-MinuteOfDay -TimeString $StartTime
$endMinutes = Get-MinuteOfDay -TimeString $EndTime

function Test-ReminderVisibility {
    param(
        [int]$CurrentMinutes,
        [int]$StartMinutes,
        [int]$EndMinutes
    )

    if ($null -eq $StartMinutes -or $null -eq $EndMinutes) {
        return $true
    }

    if ($StartMinutes -le $EndMinutes) {
        return ($CurrentMinutes -ge $StartMinutes) -and ($CurrentMinutes -lt $EndMinutes)
    } else {
        return ($CurrentMinutes -ge $StartMinutes) -or ($CurrentMinutes -lt $EndMinutes)
    }
}

$backgroundBrush = New-Object System.Windows.Media.SolidColorBrush
$backgroundBrush.Color = [System.Windows.Media.ColorConverter]::ConvertFromString($BackgroundColor)
$foregroundBrush = New-Object System.Windows.Media.SolidColorBrush
$foregroundBrush.Color = [System.Windows.Media.ColorConverter]::ConvertFromString($TextColor)

$window = New-Object System.Windows.Window
$window.Title = "Sleep Reminder"
$window.WindowStyle = 'None'
$window.ResizeMode = 'NoResize'
$window.AllowsTransparency = $true
$window.Background = [System.Windows.Media.Brushes]::Transparent
$window.Topmost = $true
$window.Width = $Width
$window.Height = $Height
$window.ShowInTaskbar = $false
$window.Focusable = $false
$window.Opacity = $Opacity
$window.WindowStartupLocation = 'CenterScreen'

$border = New-Object System.Windows.Controls.Border
$border.Background = $backgroundBrush
$border.CornerRadius = New-Object System.Windows.CornerRadius($CornerRadius)
$border.Padding = New-Object System.Windows.Thickness(24)

$grid = New-Object System.Windows.Controls.Grid

$textBlock = New-Object System.Windows.Controls.TextBlock
$textBlock.Text = $Message
$textBlock.Foreground = $foregroundBrush
$textBlock.FontSize = $FontSize
$textBlock.TextAlignment = 'Center'
$textBlock.HorizontalAlignment = 'Center'
$textBlock.VerticalAlignment = 'Center'
$textBlock.TextWrapping = 'Wrap'
$textBlock.Margin = New-Object System.Windows.Thickness(4)

$grid.Children.Add($textBlock) | Out-Null
$border.Child = $grid
$window.Content = $border

$window.Add_MouseLeftButtonDown({
    $_.Handled = $true
    Set-ReminderWindowBounds
    $window.DragMove()
    Set-ReminderWindowBounds
})

$window.Add_KeyDown({
    if ($_.Key -eq [System.Windows.Input.Key]::Escape -or (
        $_.Key -eq [System.Windows.Input.Key]::Q -and [System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::LeftCtrl)
    )) {
        $window.Close()
    }
})

$window.Add_MouseWheel({
    if ([System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::LeftCtrl) -or [System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::RightCtrl)) {
        $delta = if ($_.Delta -gt 0) { 20 } else { -20 }
        $newWidth = [Math]::Max(200, $window.Width + $delta)
        $newHeight = [Math]::Max(120, $window.Height + ($delta * 0.45))
        $window.Width = $newWidth
        $window.Height = $newHeight
        Set-ReminderWindowBounds
    }
})

$shouldShowScript = {
    $now = Get-Date
    $currentMinutes = ($now.Hour * 60) + $now.Minute
    Test-ReminderVisibility -CurrentMinutes $currentMinutes -StartMinutes $startMinutes -EndMinutes $endMinutes
}

$UpdateVisibility = {
    $shouldShow = & $shouldShowScript
    if ($shouldShow) {
        if (-not $window.IsVisible) {
            $window.Show()
        }
        if ($window.Visibility -ne [System.Windows.Visibility]::Visible) {
            $window.Visibility = [System.Windows.Visibility]::Visible
        }
    } else {
        if ($window.IsVisible) {
            $window.Hide()
        }
    }
}

$dispatcherTimer = New-Object System.Windows.Threading.DispatcherTimer
$dispatcherTimer.Interval = [TimeSpan]::FromSeconds([Math]::Max(5, $CheckIntervalSeconds))
$dispatcherTimer.Add_Tick({ & $UpdateVisibility })

$app = [System.Windows.Application]::Current
if (-not $app) {
    $app = New-Object System.Windows.Application
}
$app.ShutdownMode = [System.Windows.ShutdownMode]::OnExplicitShutdown

$window.Add_Closed({
    $dispatcherTimer.Stop()
    $app.Shutdown()
})

$window.Add_SourceInitialized({
    & $UpdateVisibility
    $dispatcherTimer.Start()
    Set-ReminderWindowBounds
})

$app.Run($window) | Out-Null
