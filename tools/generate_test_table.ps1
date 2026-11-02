# Script to parse JUnit XML test reports across all modules and output a reconciled table
$modules = @("engine", "data", "editor-core", "editor-map", "game")
$grandTotal = 0

Write-Output "| Module | Test Class | Methods |"
Write-Output "| :--- | :--- | :--- |"

foreach ($mod in $modules) {
    $dir = Join-Path $PSScriptRoot "..\$mod\build\test-results\test"
    if (Test-Path $dir) {
        $xmlFiles = Get-ChildItem -Path $dir -Filter "TEST-*.xml" | Sort-Object Name
        $modTotal = 0
        foreach ($file in $xmlFiles) {
            [xml]$xml = Get-Content $file.FullName
            $suiteName = $xml.testsuite.name
            $testCount = [int]$xml.testsuite.tests
            $modTotal += $testCount
            $grandTotal += $testCount
            Write-Output "| $mod | $suiteName | $testCount |"
        }
        Write-Output "| **$mod subtotal** | | **$modTotal** |"
    }
}

Write-Output ""
Write-Output "Grand Total Test Count: $grandTotal"
