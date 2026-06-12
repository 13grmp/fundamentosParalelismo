# README de Execucao - Mini E-commerce Distribuido

Este projeto implementa um mini e-commerce distribuido com quatro aplicacoes Spring Boot:

- `gateway`: API Gateway, porta `8080`.
- `users`: servico de usuarios, porta `5001`.
- `products`: servico de produtos, porta `5002`.
- `orders`: servico de pedidos, porta `5003`.

O Gateway e o ponto de entrada unico para os testes. Use sempre `http://localhost:8080` nas chamadas externas.

## Pre-requisitos

- Java 17 instalado.
- PowerShell.
- Maven Wrapper incluido em cada pasta (`mvnw.cmd`).
- VS Code opcional, para usar as tasks ja configuradas.
- Docker opcional. O projeto tambem pode subir com `docker compose up --build`.
- O projeto usa arquivos JSON como armazenamento local.

## Variaveis de ambiente

As aplicacoes funcionam com valores padrao, mas estas variaveis podem ser alteradas:

| Variavel | Padrao | Uso |
|---|---:|---|
| `JWT_SECRET` | `dev-secret-change-me` | Chave usada para assinar e validar JWT. Deve ser a mesma em todos os servicos. |
| `GATEWAY_PORT` | `8080` | Porta do Gateway. |
| `USERS_PORT` | `5001` | Porta do servico de usuarios. |
| `PRODUCTS_PORT` | `5002` | Porta do servico de produtos. |
| `ORDERS_PORT` | `5003` | Porta do servico de pedidos. |
| `USERS_SERVICE_URL` | `http://localhost:5001` | URL interna do servico de usuarios para o Gateway. |
| `PRODUCTS_SERVICE_URL` | `http://localhost:5002` | URL interna do servico de produtos para o Gateway e pedidos. |
| `ORDERS_SERVICE_URL` | `http://localhost:5003` | URL interna do servico de pedidos para o Gateway. |
| `USERS_STORAGE_FILE` | `data/users.json` | Arquivo JSON de usuarios. |
| `PRODUCTS_PRIMARY_STORAGE_FILE` | `data/products-primary.json` | Replica primaria de produtos. |
| `PRODUCTS_REPLICA_STORAGE_FILE` | `data/products-replica.json` | Replica secundaria de produtos. |
| `ORDERS_STORAGE_FILE` | `data/orders.json` | Arquivo JSON de pedidos. |
| `HEARTBEAT_INTERVAL_MS` | `5000` | Intervalo do heartbeat do Gateway. |
| `HEARTBEAT_MAX_FAILURES` | `2` | Falhas consecutivas antes do Gateway marcar o servico como indisponivel. |

O servico `users` cria um admin inicial automaticamente:

- Email: `admin@local`
- Senha: `admin123`

## Como subir os servicos

Abra quatro terminais PowerShell na raiz do projeto e execute:

```powershell
cd users
.\mvnw.cmd spring-boot:run
```

```powershell
cd products
.\mvnw.cmd spring-boot:run
```

```powershell
cd orders
.\mvnw.cmd spring-boot:run
```

```powershell
cd gateway
.\mvnw.cmd spring-boot:run
```

Tambem e possivel usar as tasks do VS Code:

- `run: all services`
- `run: all services with product replica`

## Como subir com Docker Compose

Na raiz do projeto, execute:

```powershell
docker compose up --build
```

Se o comando `docker` nao estiver no `PATH` do Windows, mas o Docker Desktop estiver instalado no caminho padrao, use:

```powershell
& 'C:\Program Files\Docker\Docker\resources\cli-plugins\docker-compose.exe' up --build
```

O Compose sobe os quatro servicos na mesma rede Docker:

- `gateway`: exposto em `http://localhost:8080`;
- `users`: exposto em `http://localhost:5001`;
- `products`: exposto em `http://localhost:5002`;
- `orders`: exposto em `http://localhost:5003`.

As URLs internas usadas pelos containers sao configuradas automaticamente:

- `USERS_SERVICE_URL=http://users:5001`
- `PRODUCTS_SERVICE_URL=http://products:5002`
- `ORDERS_SERVICE_URL=http://orders:5003`

Os dados JSON ficam em volumes Docker nomeados (`users-data`, `products-data` e `orders-data`). Para parar sem apagar dados:

```powershell
docker compose down
```

Para parar e limpar os volumes:

```powershell
docker compose down -v
```

O arquivo `.env.example` mostra as variaveis que podem ser copiadas para `.env` antes de subir o Compose.

Com os containers rodando, o mesmo fluxo automatizado pode ser executado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-flow.ps1
```

## Como rodar os testes

Por servico:

```powershell
cd users
.\mvnw.cmd test
```

```powershell
cd products
.\mvnw.cmd test
```

```powershell
cd orders
.\mvnw.cmd test
```

```powershell
cd gateway
.\mvnw.cmd test
```

Ou pelo VS Code:

- `test: all services`

## Fluxo completo automatizado

Com os quatro servicos rodando, execute na raiz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-flow.ps1
```

Esse script:

- cadastra um usuario comum;
- faz login do usuario;
- faz login do admin seed;
- cria produto com token de admin;
- valida bloqueio de criacao com token comum;
- lista produtos;
- cria pedido;
- lista pedidos do usuario.

## Exemplos de chamadas via Gateway

### Health do Gateway

```powershell
curl.exe http://localhost:8080/health
```

### Cadastrar usuario comum

```powershell
curl.exe -X POST http://localhost:8080/users/register `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Gabriel\",\"email\":\"gabriel@example.com\",\"password\":\"user123\"}"
```

### Login de usuario comum

```powershell
$userLogin = curl.exe -s -X POST http://localhost:8080/users/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"gabriel@example.com\",\"password\":\"user123\"}" | ConvertFrom-Json

$userToken = $userLogin.data.token
```

### Login de admin

```powershell
$adminLogin = curl.exe -s -X POST http://localhost:8080/users/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"admin@local\",\"password\":\"admin123\"}" | ConvertFrom-Json

$adminToken = $adminLogin.data.token
```

### Buscar usuario por id

```powershell
curl.exe http://localhost:8080/users/ID_DO_USUARIO `
  -H "Authorization: Bearer $userToken"
```

### Criar produto com admin

```powershell
$product = curl.exe -s -X POST http://localhost:8080/products `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $adminToken" `
  -d "{\"name\":\"Notebook\",\"description\":\"Notebook gamer\",\"price\":3999.90,\"stock\":5}" | ConvertFrom-Json

$productId = $product.data.id
```

### Tentar criar produto com usuario comum

```powershell
curl.exe -X POST http://localhost:8080/products `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $userToken" `
  -d "{\"name\":\"Produto negado\",\"description\":\"Nao deve criar\",\"price\":10,\"stock\":1}"
```

O retorno esperado e `403 Forbidden`.

### Listar produtos

```powershell
curl.exe http://localhost:8080/products
```

### Detalhar produto

```powershell
curl.exe http://localhost:8080/products/$productId
```

### Criar pedido

```powershell
$order = curl.exe -s -X POST http://localhost:8080/orders `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $userToken" `
  -d "{\"productId\":\"$productId\",\"quantity\":2}" | ConvertFrom-Json
```

### Listar pedidos do usuario

```powershell
curl.exe http://localhost:8080/orders/ID_DO_USUARIO `
  -H "Authorization: Bearer $userToken"
```

## Simular falha e heartbeat

1. Suba todos os servicos.
2. Pare o servico `orders`.
3. Aguarde cerca de 10 segundos, considerando `HEARTBEAT_INTERVAL_MS=5000` e `HEARTBEAT_MAX_FAILURES=2`.
4. Tente listar pedidos pelo Gateway:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-flow.ps1 -ExpectOrdersDown
```

O retorno esperado e `503 Service Unavailable`.

Depois, suba novamente o `orders` e rode:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-flow.ps1 -RecoveryOnly
```

O Gateway registra no log quando detecta falha e quando detecta recuperacao.

## Replicacao de produtos

O servico `products` usa duas replicas em arquivos JSON:

- `data/products-primary.json`
- `data/products-replica.json`

A estrategia adotada e consistencia forte: a criacao de produto so retorna sucesso depois que as duas replicas forem gravadas.

Para ver a nota de simulacao de falha da replica:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-flow.ps1 -ShowReplicaFailureNote
```

## Gerar pacote final

Na raiz do projeto, rode:

```powershell
$items = @('gateway','users','products','orders','scripts','docker-compose.yml','README_execucao.md','relatorio.pdf','Readme.md') | Where-Object { Test-Path $_ }
Compress-Archive -Path $items -DestinationPath Atividade1_Gabriel.zip -Force
```

O arquivo gerado sera `Atividade1_Gabriel.zip`.
