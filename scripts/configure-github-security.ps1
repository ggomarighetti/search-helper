[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Repository = "ggomarighetti/jpa-rsql-search",
    [switch]$EnableShaPinning
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is required."
}

gh auth status | Out-Null

if ($PSCmdlet.ShouldProcess($Repository, "set read-only default workflow permissions")) {
    gh api --method PUT "repos/$Repository/actions/permissions/workflow" `
        -f default_workflow_permissions=read `
        -F can_approve_pull_request_reviews=false | Out-Null
}

if ($PSCmdlet.ShouldProcess($Repository, "restrict Actions to the reviewed allowlist")) {
    gh api --method PUT "repos/$Repository/actions/permissions" `
        -F enabled=true `
        -f allowed_actions=selected | Out-Null

    $allowlist = [ordered]@{
        github_owned_allowed = $true
        verified_allowed = $false
        patterns_allowed = @(
            "codecov/codecov-action@*"
            "cue-lang/setup-cue@*"
            "googleapis/release-please-action@*"
            "ossf/scorecard-action@*"
        )
    } | ConvertTo-Json -Depth 10

    $allowlist | gh api --method PUT `
        "repos/$Repository/actions/permissions/selected-actions" `
        --input - | Out-Null
}

if ($EnableShaPinning) {
    if ($PSCmdlet.ShouldProcess($Repository, "require full commit SHAs for Actions")) {
        gh api --method PUT "repos/$Repository/actions/permissions" `
            -F enabled=true `
            -f allowed_actions=selected `
            -F sha_pinning_required=true | Out-Null
    }
}
else {
    Write-Warning "SHA pin enforcement was not enabled. Run again with -EnableShaPinning only after this branch is merged into master."
}

gh api "repos/$Repository/actions/permissions"
gh api "repos/$Repository/actions/permissions/workflow"
gh api "repos/$Repository/actions/permissions/selected-actions"
