# README de Execucao - Mini E-commerce Distribuído

Este projeto implementa um mini e-commerce distribuido com quatro aplicacoes Spring Boot:

- `gateway`: API Gateway, porta `8080`.
- `users`: servico de usuarios, porta `5001`.
- `products`: servico de produtos, porta `5002`.
- `orders`: servico de pedidos, porta `5003`.

O Gateway e o ponto de entrada único para os testes. Use sempre `http://localhost:8080` nas chamadas externas.

## Pre-requisitos

- Java 17 instalado.
- Maven Wrapper incluido em cada pasta (`mvnw.cmd`).
- Docker.
- O projeto usa arquivos JSON como armazenamento.

## Variaveis de ambiente

As aplicacoes funcionam com valores padrao, mas estas variaveis podem ser alteradas:

| Variavel | Padrao | Uso |
|---|---:|---|
| `JWT_SECRET` | `dev-secret` | Chave usada para assinar e validar JWT. |
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

- Email: `gabriel@admin.com`
- Senha: `admin123`

## Como subir os servicos

Abra quatro terminais na raiz do projeto e execute:

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

## Como subir com Docker Compose

Na raiz do projeto, execute:

```powershell
docker compose up --build
```

O Compose sobe os quatro servicos na mesma rede Docker:

- `gateway`:  `http://localhost:8080`;
- `users`:  `http://localhost:5001`;
- `products`:  `http://localhost:5002`;
- `orders`:  `http://localhost:5003`.

As URLs internas usadas pelos containers sao configuradas automaticamente:

- `USERS_SERVICE_URL=http://users:5001`
- `PRODUCTS_SERVICE_URL=http://products:5002`
- `ORDERS_SERVICE_URL=http://orders:5003`

Os dados JSON ficam em volumes Docker nomeados (`users-data`, `products-data` e `orders-data`). Para não serem apagados quando o docker for parado:

```powershell
docker compose down
```

Para parar e limpar os volumes:

```powershell
docker compose down -v
```

# Exemplos de chamadas

## Health

```powershell
Gateway
http://localhost:8080

Users
http://localhost:5001

Products
http://localhost:5002

Orders
http://localhost:5003
```

### Health
```powershell
/health
```

## Users

```powershell
Gateway
http://localhost:8080

Users
http://localhost:5001
```

### Cadastrar

```powershell
Path
/users/register

Método
POST

Payload
{
  "name":"Gabriel(User)",
  "email":"gabriel@user.com",
  "password":"user123"
}
```

### Login

```powershell
Path
/users/login

Método
POST

Payload
{
  "email":"gabriel@admin.com",
  "password":"admin123"
}
```

### Buscar por ID

```powershell
Path
/users/ID_DO_USUARIO

Método
GET

Token
Authorization: Bearer TOKEN_DO_USUARIO
```

## Products

```powershell
Gateway
http://localhost:8080

Products
http://localhost:5002
```

### Cadastrar

```powershell
Path
/products

Método
POST

Token
Authorization: Bearer TOKEN_DO_ADMIN(TEM QUE SER OBRIGATORIAMENTE ADMIN)

Payload
{
  "name":"Processador",
  "description":"Processador AM5",
  "price":100.00,
  "stock":5
}
```

### Listar produtos

```powershell
Path
/products

Método
GET
```

### Produtos único

```powershell
Path
/products/PRODUTO_ID

Método
GET
```

## Orders

```powershell
Gateway
http://localhost:8080

Orders
http://localhost:5003
```

### Criar pedido

```powershell
Path
/orders

Método
POST

Token
Authorization: Bearer TOKEN_DO_USUARIO

Payload
{
  "productId":PRODUTO_ID,
  "quantity":2
}
```

### Pedidos do usuário

```powershell
Path
/orders/ID_DO_USUARIO

Método
GET

Token
Authorization: Bearer TOKEN_DO_USUARIO
```

## Simular falha e heartbeat

1. Suba todos os servicos.
2. Pare o servico `orders`.
3. Aguarde cerca de 10 segundos, considerando `HEARTBEAT_INTERVAL_MS=5000` e `HEARTBEAT_MAX_FAILURES=2`.
4. Tente listar pedidos pelo Gateway:

```powershell
http://localhost:8080/orders/USUARIO_ID
```

O retorno esperado `503 Service Unavailable`.

Com mensagens `"success": false` ` "message": "Servico orders indisponivel"`

Depois, suba novamente o `orders`:

O Gateway vai registra no log quando detecta falha e quando detecta recuperação.

## Replicação de produtos

O servico `products` usa duas replicas em arquivos JSON:

- `data/products-primary.json`
- `data/products-replica.json`

A estrategia adotada e consistencia forte: a criação de produto só retorna sucesso depois que as duas replicas forem gravadas.