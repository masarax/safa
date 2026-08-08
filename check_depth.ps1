$lines = Get-Content 'D:\Nazmus Sakib\safa\app\src\main\java\com\safa\account\ui\screens\TransactionScreen.kt'
$d = 0
for ($i = 0; $i -lt 700; $i++) {
    $line = $lines[$i]
    for ($j = 0; $j -lt $line.Length; $j++) {
        if ($line[$j] -eq '{') { $d++ }
        if ($line[$j] -eq '}') { $d-- }
    }
    if ($i -ge 650 -and $i -le 695) {
        Write-Host "Line $($i+1): depth=$d | $($line.Trim())"
    }
}
