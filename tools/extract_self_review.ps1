# Extract sections 4 (15 non-negotiable principles) and 12 (15 anti-patterns) verbatim from AGENTS.md
$agentsFile = Join-Path $PSScriptRoot "..\AGENTS.md"
$targetFile = Join-Path $PSScriptRoot "..\docs\SELF_REVIEW.md"

$lines = [System.IO.File]::ReadAllLines($agentsFile, [System.Text.Encoding]::UTF8)
# Section 4 lines 22..36 (0-indexed: 21..35)
$sec4 = $lines[21..35]
# Section 12 lines 222..236 (0-indexed: 221..235)
$sec12 = $lines[221..235]

if ($sec4.Count -ne 15) {
    throw "Section 4 did not yield 15 lines (got $($sec4.Count))"
}
if ($sec12.Count -ne 15) {
    throw "Section 12 did not yield 15 lines (got $($sec12.Count))"
}

$out = @(
    "# Self-Review: Sections 4 and 12 from AGENTS.md (Verbatim)",
    "",
    "## Section 4: Non-negotiable principles",
    ""
) + $sec4 + @(
    "",
    "## Section 12: Anti-patterns",
    ""
) + $sec12

[System.IO.File]::WriteAllLines($targetFile, $out, [System.Text.Encoding]::UTF8)

# Verify exactly 30 review items
$items = ($sec4 + $sec12)
Write-Output "Successfully generated $targetFile with $($items.Count) review lines (15 principles + 15 anti-patterns)."
