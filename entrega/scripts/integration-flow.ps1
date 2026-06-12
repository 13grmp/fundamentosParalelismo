param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@local",
    [string]$AdminPassword = "admin123",
    [string]$UserName = "Usuario Integracao",
    [string]$UserEmail = "",
    [string]$UserPassword = "user123",
    [switch]$ExpectOrdersDown,
    [switch]$RecoveryOnly,
    [switch]$ShowReplicaFailureNote
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[integration] $Message"
}

function New-JsonBody {
    param([hashtable]$Value)
    return ($Value | ConvertTo-Json -Depth 8)
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = "",
        [int[]]$ExpectedStatus = @(200)
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $uri = "$GatewayUrl$Path"
    $bodyJson = $null
    if ($null -ne $Body) {
        $bodyJson = New-JsonBody $Body
    }

    try {
        $response = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body $bodyJson -UseBasicParsing
        $status = [int]$response.StatusCode
        $content = $response.Content
    } catch {
        if ($_.Exception.Response -eq $null) {
            throw
        }

        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $content = $reader.ReadToEnd()
        $reader.Close()
    }

    if ($ExpectedStatus -notcontains $status) {
        throw "Status inesperado em $Method $Path. Esperado: $($ExpectedStatus -join ', '), recebido: $status. Corpo: $content"
    }

    if ([string]::IsNullOrWhiteSpace($content)) {
        return [pscustomobject]@{ status = $status; body = $null }
    }

    return [pscustomobject]@{
        status = $status
        body = ($content | ConvertFrom-Json)
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Get-UserIdFromToken {
    param([string]$Token)
    $payload = $Token.Split(".")[1]
    $payload = $payload.Replace("-", "+").Replace("_", "/")
    switch ($payload.Length % 4) {
        2 { $payload += "==" }
        3 { $payload += "=" }
    }
    $json = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
    return ($json | ConvertFrom-Json).userId
}

if ([string]::IsNullOrWhiteSpace($UserEmail)) {
    $suffix = Get-Date -Format "yyyyMMddHHmmss"
    $UserEmail = "usuario.integracao.$suffix@example.com"
}

Write-Step "Gateway alvo: $GatewayUrl"
Write-Step "Verificando /health do gateway"
Invoke-JsonRequest -Method "GET" -Path "/health" -ExpectedStatus @(200) | Out-Null

Write-Step "Cadastrando usuario comum: $UserEmail"
$register = Invoke-JsonRequest -Method "POST" -Path "/users/register" -ExpectedStatus @(201, 409) -Body @{
    name = $UserName
    email = $UserEmail
    password = $UserPassword
}
if ($register.status -eq 409) {
    Write-Step "Usuario comum ja existia, seguindo para login"
}

Write-Step "Fazendo login do usuario comum"
$userLogin = Invoke-JsonRequest -Method "POST" -Path "/users/login" -ExpectedStatus @(200) -Body @{
    email = $UserEmail
    password = $UserPassword
}
$userToken = $userLogin.body.data.token
$userId = Get-UserIdFromToken $userToken
Assert-True -Condition ($userToken.Split(".").Length -eq 3) -Message "JWT do usuario comum nao tem formato valido"

Write-Step "Fazendo login do admin seed: $AdminEmail"
$adminLogin = Invoke-JsonRequest -Method "POST" -Path "/users/login" -ExpectedStatus @(200) -Body @{
    email = $AdminEmail
    password = $AdminPassword
}
$adminToken = $adminLogin.body.data.token
Assert-True -Condition ($adminToken.Split(".").Length -eq 3) -Message "JWT do admin nao tem formato valido"

if ($ExpectOrdersDown) {
    Write-Step "Validando que orders esta indisponivel via gateway"
    Invoke-JsonRequest -Method "GET" -Path "/orders/$userId" -Token $userToken -ExpectedStatus @(503) | Out-Null
    Write-Step "OK: gateway retornou 503 para orders indisponivel"
    exit 0
}

Write-Step "Criando produto com token de admin"
$productName = "Produto Integracao $(Get-Date -Format 'HHmmss')"
$productCreate = Invoke-JsonRequest -Method "POST" -Path "/products" -Token $adminToken -ExpectedStatus @(201) -Body @{
    name = $productName
    description = "Produto criado pelo fluxo integrado"
    price = 123.45
    stock = 4
}
$productId = $productCreate.body.data.id
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($productId)) -Message "Produto criado sem id"

Write-Step "Bloqueando criacao de produto com token de usuario comum"
Invoke-JsonRequest -Method "POST" -Path "/products" -Token $userToken -ExpectedStatus @(403) -Body @{
    name = "Produto Negado"
    description = "Este produto nao deve ser criado"
    price = 10
    stock = 1
} | Out-Null

Write-Step "Listando produtos"
$products = Invoke-JsonRequest -Method "GET" -Path "/products" -ExpectedStatus @(200)
Assert-True -Condition ($products.body.data.Count -ge 1) -Message "Lista de produtos vazia apos criacao"

Write-Step "Criando pedido com usuario autenticado"
$orderCreate = Invoke-JsonRequest -Method "POST" -Path "/orders" -Token $userToken -ExpectedStatus @(201) -Body @{
    productId = $productId
    quantity = 2
}
Assert-True -Condition ($orderCreate.body.data.productId -eq $productId) -Message "Pedido nao foi vinculado ao produto criado"
Assert-True -Condition ($orderCreate.body.data.userId -eq $userId) -Message "Pedido nao foi vinculado ao usuario autenticado"

Write-Step "Listando pedidos do usuario autenticado"
$orders = Invoke-JsonRequest -Method "GET" -Path "/orders/$userId" -Token $userToken -ExpectedStatus @(200)
Assert-True -Condition ($orders.body.data.Count -ge 1) -Message "Lista de pedidos vazia apos criacao"

if ($RecoveryOnly) {
    Write-Step "OK: orders respondeu novamente apos recuperacao"
}

if ($ShowReplicaFailureNote) {
    Write-Step "Replica de produtos: a implementacao usa duas replicas em arquivos JSON no mesmo processo."
    Write-Step "Com consistencia forte, se a escrita em uma replica falhar, POST /products retorna erro e nao confirma sucesso ao cliente."
    Write-Step "Para simular manualmente, reinicie products com PRODUCTS_REPLICA_STORAGE_FILE apontando para um caminho sem permissao de escrita e tente criar produto via admin."
}

Write-Step "Fluxo integrado concluido com sucesso"
