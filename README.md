# Freela Marketplace

Projeto de referência para um marketplace de contratação de freelancers construído com arquitetura de microsserviços em Java e Spring.

A aplicação representa um cenário em que clientes contratam freelancers para a execução de trabalhos. O núcleo do sistema é o gerenciamento dos contratos firmados entre as partes. A partir desse domínio, outros serviços mantêm informações relacionadas a notificações, reputação e auditoria.

## Visão geral

O sistema é composto por seis aplicações Spring Boot:

- `eureka-server`: registro e descoberta dos serviços.
- `api-gateway`: ponto de entrada HTTP da aplicação.
- `contrato-service`: gerenciamento dos contratos entre clientes e freelancers.
- `notificacao-service`: armazenamento das notificações relacionadas aos contratos.
- `reputacao-service`: manutenção de informações agregadas sobre os freelancers.
- `auditoria-service`: registro de eventos relevantes do sistema.

A infraestrutura local utiliza PostgreSQL e Apache Kafka.

```text
                         +-------------------+
                         |      Cliente      |
                         +---------+---------+
                                   |
                                   | HTTP
                                   v
                         +-------------------+
                         |    API Gateway    |
                         |       :8080       |
                         +---------+---------+
                                   |
                     Service Discovery / Eureka
                                   |
                +------------------+------------------+
                |                  |                  |
                v                  v                  v
       +----------------+  +----------------+  +----------------+
       | contrato       |  | notificacao    |  | reputacao      |
       | service :8081  |  | service :8082  |  | service :8083  |
       +----------------+  +----------------+  +----------------+
                |
                |                       +----------------+
                +---------------------->| auditoria      |
                                        | service :8084  |
                                        +----------------+

                          +-------------------+
                          |       Kafka       |
                          |       :9092       |
                          +-------------------+

                          +-------------------+
                          |    PostgreSQL     |
                          |       :5432       |
                          +-------------------+
```

## Domínio

O domínio principal está no `contrato-service`.

Um contrato representa o vínculo entre um cliente e um freelancer para a execução de um trabalho. Cada contrato possui:

- identificador;
- cliente;
- freelancer;
- título do trabalho;
- valor;
- status;
- data de criação.

Os estados disponíveis são:

```text
ATIVO
ENTREGA_REGISTRADA
CONCLUIDO
CANCELADO
```

O fluxo de negócio previsto pelo modelo é:

```text
ATIVO
  |
  v
ENTREGA_REGISTRADA
  |
  v
CONCLUIDO
```

Um contrato ativo também pode ser cancelado.

O `contrato-service` utiliza uma organização inspirada em Domain-Driven Design, separando domínio, aplicação e infraestrutura.

```text
contrato-service
└── src/main/java/br/com/freela/contrato
    ├── application
    ├── domain
    │   ├── event
    │   ├── model
    │   ├── repository
    │   └── shared
    └── infrastructure
        ├── persistence
        └── web
```

O Aggregate `Contrato` concentra as regras relacionadas às mudanças de estado e produz eventos de domínio para criação, registro de entrega, conclusão e cancelamento.

## Serviços

### contrato-service

Responsável pelo ciclo de vida dos contratos.

Porta:

```text
8081
```

Banco:

```text
contrato_db
```

Principais recursos HTTP:

```text
POST /api/contratos
GET  /api/contratos
GET  /api/contratos/{id}
POST /api/contratos/{id}/entrega
POST /api/contratos/{id}/conclusao
POST /api/contratos/{id}/cancelamento
```

Exemplo de criação de contrato:

```json
{
  "clienteId": "11111111-1111-1111-1111-111111111111",
  "freelancerId": "22222222-2222-2222-2222-222222222222",
  "titulo": "Construção de API de pagamentos",
  "valor": 3500.00
}
```

### notificacao-service

Mantém notificações relacionadas aos acontecimentos do marketplace.

Porta:

```text
8082
```

Banco:

```text
notificacao_db
```

As notificações armazenam informações como contrato, destinatário, tipo, mensagem e momento de criação.

### reputacao-service

Mantém informações agregadas sobre a atividade dos freelancers.

Porta:

```text
8083
```

Banco:

```text
reputacao_db
```

Para cada freelancer são mantidos dados como quantidade de contratos concluídos e valor total dos contratos registrados.

Endpoint disponível para consulta:

```text
GET /api/reputacoes
```

### auditoria-service

Responsável pelo armazenamento de registros associados aos eventos do sistema.

Porta:

```text
8084
```

Banco:

```text
auditoria_db
```

Cada registro de auditoria pode armazenar:

- `eventId`;
- `aggregateId`;
- tipo do evento;
- `correlationId`;
- payload original;
- horário de recebimento.

Endpoint disponível para consulta:

```text
GET /api/auditoria
```

## API Gateway

O `api-gateway` é o ponto de entrada HTTP para os microsserviços.

Porta:

```text
8080
```

As rotas configuradas são:

| Caminho | Serviço |
|---|---|
| `/api/contratos/**` | `contrato-service` |
| `/api/notificacoes/**` | `notificacao-service` |
| `/api/reputacoes/**` | `reputacao-service` |
| `/api/auditoria/**` | `auditoria-service` |

O Gateway utiliza Eureka para localizar as instâncias dos serviços.

Também existe suporte ao header:

```text
X-Correlation-Id
```

Quando o header não é enviado pelo cliente, o Gateway gera automaticamente um UUID e o encaminha para o serviço de destino.

## Eureka Server

O Eureka Server mantém o registro das aplicações disponíveis no ambiente.

Porta:

```text
8761
```

Interface web:

```text
http://localhost:8761
```

Os microsserviços utilizam, por padrão:

```text
http://localhost:8761/eureka/
```

como endereço do service registry.

## PostgreSQL

O ambiente utiliza uma única instância PostgreSQL com bancos separados para cada serviço.

```text
Host:     localhost
Porta:    5432
Usuário:  freela
Senha:    freela
```

Bancos criados durante a inicialização:

```text
contrato_db
notificacao_db
reputacao_db
auditoria_db
```

O script de criação dos bancos está em:

```text
infra/postgres/init-databases.sql
```

Os serviços utilizam Hibernate com `ddl-auto: update` para criação e atualização das tabelas locais.

## Apache Kafka

O Apache Kafka é executado em modo KRaft, sem ZooKeeper.

Para aplicações executadas diretamente na máquina:

```text
localhost:9092
```

Para aplicações executadas dentro da rede Docker:

```text
kafka:19092
```

O broker possui listeners separados para comunicação interna e externa.

O ambiente também inclui o Kafka UI.

```text
http://localhost:8090
```

### Eventos do ciclo de vida dos contratos

O `contrato-service` grava os eventos na tabela `contrato_event_outbox` na mesma transação que persiste o contrato. Um publicador em background envia as mensagens pendentes para Kafka usando `contratoId` como chave. A mensagem só é marcada como publicada após confirmação do broker; falhas permanecem na outbox para nova tentativa. Esse mecanismo fornece entrega pelo menos uma vez, portanto os consumidores são idempotentes por `eventId`.

| Tópico | Produtor | Consumidores | Partições | Chave |
|---|---|---|---:|---|
| `freela.contrato.eventos.v1` | `contrato-service` | `notificacao-service`, `reputacao-service`, `auditoria-service` | 3 | `contratoId` |

O particionamento por `contratoId` mantém os eventos de um mesmo contrato na mesma partição; instâncias concorrentes processam partições distintas. Os três consumidores usam grupos próprios, permitindo que cada serviço receba todos os eventos. Notificações são geradas para ambos os participantes em criação, conclusão e cancelamento, e para o cliente quando uma entrega é registrada. Reputação reage somente à conclusão. Auditoria registra todos os eventos.

#### Contrato de mensagem

Todos os eventos são mensagens JSON no tópico `freela.contrato.eventos.v1`. O produtor é o `contrato-service`; a chave Kafka é o UUID de `contratoId` codificado como string UTF-8. Essa chave mantém eventos do mesmo contrato na mesma partição.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `eventId` | UUID | Sim | Identificador único do evento, usado na deduplicação. |
| `eventType` | string | Sim | Nome do evento; valores listados na tabela abaixo. |
| `aggregateType` | string | Sim | Tipo do agregado; sempre `Contrato`. |
| `contratoId` | UUID | Sim | Identificador do contrato e chave Kafka. |
| `occurredAt` | string ISO-8601 UTC | Sim | Data e hora em que a transição ocorreu. |
| `correlationId` | UUID/string | Sim | Correlação recebida em `X-Correlation-Id` ou gerada pelo produtor. |
| `data.clienteId` | UUID | Sim | Cliente associado ao contrato. |
| `data.freelancerId` | UUID | Sim | Freelancer associado ao contrato. |
| `data.titulo` | string | Sim | Título do contrato. |
| `data.valor` | decimal | Sim | Valor monetário do contrato. |

`eventId`, `occurredAt` e `contratoId` aparecem apenas no envelope, não dentro de `data`.

| Nome do evento (`eventType`) | Produtor | Consumidores | Dados específicos |
|---|---|---|---|
| `ContratoCriado` | `contrato-service` | `notificacao-service` (notifica cliente e freelancer), `auditoria-service` | Campos comuns de `data`. |
| `EntregaRegistrada` | `contrato-service` | `notificacao-service` (notifica cliente), `auditoria-service` | Campos comuns de `data`. |
| `ContratoConcluido` | `contrato-service` | `notificacao-service` (notifica cliente e freelancer), `reputacao-service`, `auditoria-service` | Campos comuns de `data`; reputação incrementa o histórico do freelancer pelo valor. |
| `ContratoCancelado` | `contrato-service` | `notificacao-service` (notifica cliente e freelancer), `auditoria-service` | Campos comuns de `data`. |

#### Exemplos de mensagens

Os exemplos usam IDs fictícios. Em todos eles, envie a chave Kafka `4dfb2880-082b-41e8-9c88-750e6f75f77b` e o payload JSON mostrado.

`ContratoCriado`:

```json
{
  "eventId": "1ca6f947-1424-4eac-8978-bfc2ddbc988e",
  "eventType": "ContratoCriado",
  "aggregateType": "Contrato",
  "contratoId": "4dfb2880-082b-41e8-9c88-750e6f75f77b",
  "occurredAt": "2026-10-10T19:45:00Z",
  "correlationId": "f973e469-4fc5-4710-b0d4-559f3c39bf84",
  "data": {
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Construção de API de pagamentos",
    "valor": 3500.00
  }
}
```

`EntregaRegistrada` (envelope idêntico; cada ocorrência tem seu próprio `eventId` e `occurredAt`):

```json
{
  "eventId": "3a1e0c06-b149-4a71-baf8-4f09a5886900",
  "eventType": "EntregaRegistrada",
  "aggregateType": "Contrato",
  "contratoId": "4dfb2880-082b-41e8-9c88-750e6f75f77b",
  "occurredAt": "2026-10-11T14:30:00Z",
  "correlationId": "f973e469-4fc5-4710-b0d4-559f3c39bf84",
  "data": {
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Construção de API de pagamentos",
    "valor": 3500.00
  }
}
```

`ContratoConcluido`:

```json
{
  "eventId": "47bdc4c5-8d8b-4266-a9c3-30d3f190e1f4",
  "eventType": "ContratoConcluido",
  "aggregateType": "Contrato",
  "contratoId": "4dfb2880-082b-41e8-9c88-750e6f75f77b",
  "occurredAt": "2026-10-12T16:00:00Z",
  "correlationId": "f973e469-4fc5-4710-b0d4-559f3c39bf84",
  "data": {
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Construção de API de pagamentos",
    "valor": 3500.00
  }
}
```

`ContratoCancelado`:

```json
{
  "eventId": "91826c71-44fa-4748-a24f-256c3a0a2f82",
  "eventType": "ContratoCancelado",
  "aggregateType": "Contrato",
  "contratoId": "4dfb2880-082b-41e8-9c88-750e6f75f77b",
  "occurredAt": "2026-10-12T16:30:00Z",
  "correlationId": "f973e469-4fc5-4710-b0d4-559f3c39bf84",
  "data": {
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Construção de API de pagamentos",
    "valor": 3500.00
  }
}
```

Para manter a correlação, as chamadas HTTP podem enviar `X-Correlation-Id`; quando ausente, o serviço gera um UUID. O tópico pode ser substituído por `CONTRATO_EVENTOS_TOPIC`; os endereços de bootstrap seguem `KAFKA_BOOTSTRAP_SERVERS`.

As transições são acionadas com `POST /api/contratos/{id}/entrega`, `POST /api/contratos/{id}/conclusao` e `POST /api/contratos/{id}/cancelamento`. Exemplo: após criar o contrato, registre a entrega e conclua-o para publicar os eventos correspondentes.

O `auditoria-service` persiste `eventId`, `eventType`, `contratoId`, `occurredAt`, `correlationId`, o payload original e o horário de recebimento. Esses campos podem ser consultados em `GET /api/auditoria`; `occurredAt` representa a data e hora do evento, enquanto `recebidoEm` indica quando a auditoria o processou.

## Logs

Todos os serviços utilizam logs em nível `INFO` com um formato comum contendo data, nível, nome da aplicação, thread, logger e mensagem.

Exemplo:

```text
2026-09-14 14:42:18.431 INFO service=contrato-service thread=http-nio-8081-exec-1 logger=b.c.f.c.a.ContratoApplicationService - contrato.criacao.inicio clienteId=... freelancerId=...
```

O código registra pontos importantes do fluxo, incluindo:

```text
gateway.request.inicio
gateway.request.fim
http.contrato.criar
contrato.criacao.inicio
contrato.dominio.criado
contrato.persistence.save.inicio
contrato.persistence.save.sucesso
contrato.evento.outbox
contrato.evento.publicado
notificacao.evento.sucesso
reputacao.evento.sucesso
auditoria.registro.sucesso
contrato.criacao.sucesso
reputacao.atualizacao.inicio
reputacao.atualizacao.sucesso
auditoria.registro.inicio
auditoria.registro.sucesso
```

A presença do `correlationId` nas chamadas HTTP permite relacionar logs produzidos durante uma mesma requisição.

## Infraestrutura local

Os serviços de infraestrutura estão definidos em:

```text
infra/docker-compose.yml
```

Para iniciar o ambiente:

```bash
cd infra
docker compose up -d
```

Para verificar os containers:

```bash
docker compose ps
```

Para encerrar:

```bash
docker compose down
```

Os dados do PostgreSQL são mantidos em volume Docker.

Para remover também os dados persistidos:

```bash
docker compose down -v
```

## Execução das aplicações

A partir da raiz do projeto, cada módulo pode ser iniciado separadamente com Maven.

Eureka Server:

```bash
mvn -pl eureka-server spring-boot:run
```

API Gateway:

```bash
mvn -pl api-gateway spring-boot:run
```

Contrato Service:

```bash
mvn -pl contrato-service spring-boot:run
```

Notificação Service:

```bash
mvn -pl notificacao-service spring-boot:run
```

Reputação Service:

```bash
mvn -pl reputacao-service spring-boot:run
```

Auditoria Service:

```bash
mvn -pl auditoria-service spring-boot:run
```

## Portas

| Componente | Porta |
|---|---:|
| API Gateway | `8080` |
| contrato-service | `8081` |
| notificacao-service | `8082` |
| reputacao-service | `8083` |
| auditoria-service | `8084` |
| Eureka Server | `8761` |
| Kafka | `9092` |
| Kafka UI | `8090` |
| PostgreSQL | `5432` |

## Teste básico

Com a infraestrutura e as aplicações em execução, um contrato pode ser criado pelo Gateway:

```bash
curl -i -X POST http://localhost:8080/api/contratos \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: teste-contrato-001' \
  -d '{
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Construção de API de pagamentos",
    "valor": 3500.00
  }'
```

Consulta dos contratos:

```bash
curl http://localhost:8080/api/contratos
```

Consulta de um contrato específico:

```bash
curl http://localhost:8080/api/contratos/{id}
```

## Tecnologias

```text
Java 21
Spring Boot 4.1
Spring Cloud
Spring Cloud Gateway
Netflix Eureka
Spring Data JPA
PostgreSQL 16
Apache Kafka 4
Docker Compose
Maven
```
