# Windows Sleep Reminder Overlay

A lightweight, borderless, always-on-top reminder window you can drag anywhere on the screen. It mirrors the Android sleep reminder overlay but runs natively on Windows 11 using only PowerShell + WPF (already built into Windows).

## What You Get

- Movable floating window with your custom message
- Optional time window (e.g. show only between 22:30 and 07:00)
- Semi-transparent rounded background and configurable colors
- Quick resize with `Ctrl + mouse wheel`
- Close via `Esc` or `Ctrl + Q`
- Automatically re-pins itself to whatever virtual desktop you switch to (uses Windows' virtual desktop API every few seconds)

## Files

```
sleep-reminder-windows/
├─ SleepReminder.ps1   # PowerShell script that shows the floating window
└─ README.md           # this guide
```

## Requirements

- Windows 11 (works on Windows 10 too)
- PowerShell 5.1 (built-in) or PowerShell 7+
- No additional SDKs/IDEs required

## First-Time Setup

1. Open **Windows PowerShell** (press `Win`, type "PowerShell", run as needed).
2. Allow scripts in your current session (skip if already allowed):
   ```powershell
   Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
   ```
3. Navigate into the project folder:
   ```powershell
   cd "C:\Users\mihai\AndroidStudioProjects\App_Blocker2\sleep-reminder-windows"
   ```

## Run the Reminder

Start the overlay with default settings:
```powershell
powershell -ExecutionPolicy Bypass -File .\SleepReminder.ps1
```

Or launch with customized options:
```powershell
powershell -ExecutionPolicy Bypass -File .\SleepReminder.ps1 \
    -Message "Put the phone down and sleep" \
    -Width 520 -Height 200 \
    -Opacity 0.9 \
    -BackgroundColor "#CC003366" \
    -TextColor "#FFFFEEDD" \
    -StartTime "22:30" -EndTime "07:00"
```

### Available Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `-Message` | `"Time to sleep!"` | Text shown inside the window (wrapping supported). |
| `-Width` / `-Height` | `840` / `320` | Starting size in pixels. Use `Ctrl + mouse wheel` to resize interactively. |
| `-Opacity` | `0.88` | Window opacity (0.0 = fully transparent, 1.0 = opaque). |
| `-BackgroundColor` | `"#CC1A1A3A"` | ARGB hex color (`AA` for alpha + RGB). |
| `-TextColor` | `"#FFFFFFFF"` | Text color in ARGB hex. |
| `-FontSize` | `54` | Text size in points. |
| `-CornerRadius` | `18` | Border corner roundness in pixels. |
| `-StartTime` / `-EndTime` | *(none)* | Optional 24h `HH:mm` values. When both provided, the window only shows within that interval (wraps over midnight if end time is earlier than start). |
| `-CheckIntervalSeconds` | `5` | How often (seconds) the script checks whether it should show/hide **and** reattach to the active virtual desktop (minimum 2 seconds). |

### Set Your Own Defaults

Rather than retyping parameters, feel free to edit the default values at the top of `SleepReminder.ps1` (e.g. change the `param` block’s `Width`, `Height`, `Message`, etc.). Save the file and future launches will pick up your preferred size and styling automatically.

## Interacting with the Window

- **Drag** anywhere on the reminder to reposition it.
- **Resize**: Hold `Ctrl` while scrolling the mouse wheel.
- **Close**: Press `Esc` or press `Ctrl + Q`.
- The window is always on top, does not steal keyboard focus, and re-anchors itself to the desktop you are actively using.

## Keep It Running Automatically

### Option 1 – Task Scheduler (recommended)
1. Open *Task Scheduler* → *Create Basic Task*.
2. Name it “Sleep Reminder Overlay”.
3. Trigger: **When I log on**.
4. Action: **Start a program**
   - *Program/script*: `powershell.exe`
   - *Add arguments*: `-WindowStyle Hidden -ExecutionPolicy Bypass -File "C:\Users\mihai\AndroidStudioProjects\App_Blocker2\sleep-reminder-windows\SleepReminder.ps1" -StartTime "22:30" -EndTime "07:00"`
5. Finish, then open the task’s **General** tab → enable **Run with highest privileges** so it launches even when UAC prompts would otherwise block it.

Prefer doing it from PowerShell? Run:
```powershell
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument '-WindowStyle Hidden -ExecutionPolicy Bypass -File "C:\Users\mihai\AndroidStudioProjects\App_Blocker2\sleep-reminder-windows\SleepReminder.ps1" -StartTime "22:30" -EndTime "07:00"'
$trigger = New-ScheduledTaskTrigger -AtLogOn
Register-ScheduledTask -TaskName "SleepReminderOverlay" -Action $action -Trigger $trigger -Description "Keeps the sleep reminder floating window running" -RunLevel Highest
```

### Option 2 – Startup Folder

1. Press `Win + R`, type `shell:startup`, press Enter.
2. Create a shortcut that points to:
   ```
   powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File "C:\Users\mihai\AndroidStudioProjects\App_Blocker2\sleep-reminder-windows\SleepReminder.ps1"
   ```
3. The overlay will now launch silently whenever you sign in.

## Troubleshooting

- If the window never appears, make sure the current time is inside the configured interval or run without `-StartTime/-EndTime` to test.
- To revert the execution policy change, run `Set-ExecutionPolicy Restricted -Scope CurrentUser`.
- Custom colors must be in ARGB hex (eight characters). Use a color picker to generate values.

That’s it—you now have a Windows version of the floating sleep reminder without needing any extra development tools.
