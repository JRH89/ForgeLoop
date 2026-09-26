param([Parameter(Mandatory=$true)][uri]$BaseUrl)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$handler.UseCookies = $false
$client = [System.Net.Http.HttpClient]::new($handler)
try {
    # Exercise the deployed proxy without a login cookie, not Vite/dev-mode auth.
    $pairing = $client.GetAsync([uri]::new($BaseUrl, '/app/runner-connect')).GetAwaiter().GetResult()
    if ([int]$pairing.StatusCode -ne 200) { throw "Anonymous pairing shell redirected or failed: $($pairing.StatusCode)" }
    $dashboard = $client.GetAsync([uri]::new($BaseUrl, '/app')).GetAwaiter().GetResult()
    if ([int]$dashboard.StatusCode -ne 302) { throw 'Dashboard must still require login. Run against a GitHub-auth deployment.' }
    # Malformed input cannot enroll anything even if authorization regresses.
    $body = '{"query":"mutation { approveRunnerPairing(challenge:\"invalid\", name:\"anonymous regression probe\") }"}'
    $content = [System.Net.Http.StringContent]::new($body, [System.Text.Encoding]::UTF8, 'application/json')
    $response = $client.PostAsync([uri]::new($BaseUrl, '/graphql'), $content).GetAwaiter().GetResult()
    $result = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    if (!$result.errors -or $result.data.approveRunnerPairing -eq $true) { throw 'Anonymous approval was not rejected.' }
    Write-Output 'PASS: anonymous pairing renders, dashboard requires login, anonymous approval is rejected.'
} finally { $client.Dispose(); $handler.Dispose() }
