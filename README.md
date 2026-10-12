# Freela Marketplace

Marketplace de freelancers desenvolvido em Java 21, Spring Boot e arquitetura de microsserviços. O serviço de contratos publica eventos no Kafka; notificações, reputação e auditoria os processam de forma independente.

## Serviços

| Serviço | Responsabilidade | Porta |
|---|---|---:|
| `api-gateway` | Entrada HTTP e roteamento | 8080 |
| `contrato-service` | Criar e atualizar contratos | 8081 |
| `notificacao-service` | Registrar notificações | 8082 |
| `reputacao-service` | Atualizar histórico do freelancer | 8083 |
| `auditoria-service` | Armazenar eventos recebidos | 8084 |
| `eureka-server` | Descoberta de serviços | 8761 |

O PostgreSQL usa a porta `5432`; Kafka, `9092`; Kafka UI, `8090`. A interface do Eureka fica em `http://localhost:8761`.

## Iniciar infraestrutura

É necessário ter Docker e Docker Compose instalados. Na raiz do projeto:

```bash
docker compose -f infra/docker-compose.yml up -d
```

Confira os containers:

```bash
docker compose -f infra/docker-compose.yml ps
```

Para encerrar:

```bash
docker compose -f infra/docker-compose.yml down
```

Para remover também os dados persistidos:

```bash
docker compose -f infra/docker-compose.yml down -v
```

O PostgreSQL cria um banco separado para cada serviço. Kafka funciona em modo KRaft, sem ZooKeeper. O Kafka UI fica em `http://localhost:8090`.

## Iniciar aplicações

Com Maven instalado, abra um terminal na raiz do projeto e inicie cada módulo em um terminal separado:

```bash
mvn -pl eureka-server spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl contrato-service spring-boot:run
mvn -pl notificacao-service spring-boot:run
mvn -pl reputacao-service spring-boot:run
mvn -pl auditoria-service spring-boot:run
```

As aplicações locais usam por padrão `localhost` para PostgreSQL, Kafka e Eureka. Os endereços podem ser alterados pelas variáveis `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS` e `EUREKA_URL`. Os serviços se registram no Eureka usando o endereço IP da máquina; isso evita falhas de resolução do hostname em ambientes Windows. Esse comportamento pode ser controlado com `EUREKA_PREFER_IP_ADDRESS`.

## Contratos

O contrato começa em `ATIVO`. A entrega muda o status para `ENTREGA_REGISTRADA`, e a conclusão muda para `CONCLUIDO`. Um contrato ainda não concluído pode ser cancelado.

| Operação | Endpoint |
|---|---|
| Criar contrato | `POST /api/contratos` |
| Listar contratos | `GET /api/contratos` |
| Consultar contrato | `GET /api/contratos/{id}` |
| Registrar entrega | `POST /api/contratos/{id}/entrega` |
| Concluir contrato | `POST /api/contratos/{id}/conclusao` |
| Cancelar contrato | `POST /api/contratos/{id}/cancelamento` |

As rotas também estão disponíveis através do Gateway na porta `8080`. Os comandos abaixo usam Bash, `curl` e `jq`. Mantenha o mesmo `X-Correlation-Id` para localizar toda a sequência nos logs.

### Fluxo de criação até conclusão

Crie um contrato. O comando guarda o `id` retornado para usar nas próximas chamadas:

```bash
CORRELATION_ID="teste-contrato-001"
CONTRATO_ID=$(curl -sS -X POST http://localhost:8080/api/contratos \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d '{
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Construção de API de pagamentos",
    "valor": 3500.00
  }' | jq -r '.id')

echo "Contrato criado: $CONTRATO_ID"
```

Liste contratos e consulte o contrato recém-criado:

```bash
curl -sS http://localhost:8080/api/contratos \
  -H "X-Correlation-Id: $CORRELATION_ID"

curl -sS "http://localhost:8080/api/contratos/$CONTRATO_ID" \
  -H "X-Correlation-Id: $CORRELATION_ID"
```

Registre a entrega e depois conclua o mesmo contrato. A conclusão só é permitida após a entrega:

```bash
curl -sS -X POST "http://localhost:8080/api/contratos/$CONTRATO_ID/entrega" \
  -H "X-Correlation-Id: $CORRELATION_ID"

curl -sS -X POST "http://localhost:8080/api/contratos/$CONTRATO_ID/conclusao" \
  -H "X-Correlation-Id: $CORRELATION_ID"
```

### Fluxo alternativo de cancelamento

Um contrato concluído não pode ser cancelado. Para testar o cancelamento, crie outro contrato e cancele-o enquanto ainda estiver ativo:

```bash
CONTRATO_CANCELAR_ID=$(curl -sS -X POST http://localhost:8080/api/contratos \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d '{
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "Manutenção de documentação",
    "valor": 500.00
  }' | jq -r '.id')

curl -sS -X POST "http://localhost:8080/api/contratos/$CONTRATO_CANCELAR_ID/cancelamento" \
  -H "X-Correlation-Id: $CORRELATION_ID"
```

O Gateway gera um `X-Correlation-Id` se a chamada não enviar um.

### Respostas de erro

Para ver o status HTTP, os cabeçalhos e o corpo retornado quando uma chamada falha, use `-i --fail-with-body`. Por exemplo, um título vazio viola a validação do contrato:

```bash
curl -i --fail-with-body -X POST http://localhost:8080/api/contratos \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: teste-contrato-erro-001" \
  -d '{
    "clienteId": "11111111-1111-1111-1111-111111111111",
    "freelancerId": "22222222-2222-2222-2222-222222222222",
    "titulo": "",
    "valor": 3500.00
  }'
```

A resposta de validação aparece no `curl` neste formato (o cabeçalho `Date` e o horário variam):

```http
HTTP/1.1 400
Content-Type: application/json

{
  "timestamp": "2026-10-12T04:08:45.077Z",
  "status": 400,
  "error": "Bad Request",
  "message": "A requisição contém campos inválidos.",
  "path": "/api/contratos",
  "correlationId": "teste-contrato-erro-001",
  "details": {
    "titulo": "must not be blank"
  }
}
```

As APIs dos serviços retornam esse formato de erro com timestamp, status, mensagem, caminho e identificador de correlação. `details` lista campos inválidos quando se trata de erro de validação e fica vazio nos outros casos.

Status mais comuns:

| Status | Significado |
|---|---|
| `400 Bad Request` | JSON, parâmetro ou campo enviado inválido |
| `404 Not Found` | Contrato não encontrado |
| `409 Conflict` | A operação não é permitida no estado atual do contrato |
| `500 Internal Server Error` | Falha inesperada; a resposta não expõe detalhes internos |

`-i` mostra o status e os cabeçalhos; `--fail-with-body` mantém o corpo da resposta visível e também informa ao shell que a requisição falhou.

## Eventos Kafka

O produtor grava cada evento na outbox do `contrato-service`, na mesma transação da alteração do contrato. Um processo em segundo plano publica os eventos na confirmação do Kafka. Se houver falha, a outbox permite nova tentativa; por isso os consumidores tratam duplicatas.

| Tópico | Produtor | Chave | Partições |
|---|---|---|---:|
| `freela.contrato.eventos.v1` | `contrato-service` | `contratoId` | 3 |

| Evento | Consumidores |
|---|---|
| `ContratoCriado` | Notificações, auditoria |
| `EntregaRegistrada` | Notificações, auditoria |
| `ContratoConcluido` | Notificações, reputação, auditoria |
| `ContratoCancelado` | Notificações, auditoria |

Notificações são registradas para cliente e freelancer na criação, conclusão e cancelamento; a entrega notifica o cliente. Reputação é atualizada na conclusão. Auditoria registra todos os eventos.

Todas as mensagens usam o mesmo envelope JSON:

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

Em cada evento:

- `eventId` identifica unicamente a mensagem.
- `eventType` informa qual operação ocorreu.
- `contratoId` também é a chave Kafka.
- `occurredAt` registra quando o evento aconteceu.
- `correlationId` relaciona a operação entre serviços.
- `data` contém cliente, freelancer, título e valor do contrato.

A chave `contratoId` mantém os eventos de um mesmo contrato na mesma partição e preserva sua ordem. Partições diferentes podem ser processadas em paralelo. A concorrência padrão de cada consumidor é três e pode ser alterada com `KAFKA_CONSUMER_CONCURRENCY`.

Os consumidores usam `eventId` para evitar efeitos duplicados. Notificações usam também o destinatário na chave, para permitir uma notificação por participante. Auditoria e reputação persistem o identificador processado junto com seus efeitos.

O tópico pode ser alterado com `CONTRATO_EVENTOS_TOPIC`.

### Falhas e reprocessamento

Se um consumidor falhar, a mesma mensagem é tentada novamente duas vezes, com intervalo de um segundo entre as tentativas (três tentativas no total, contando a primeira). Depois disso, a mensagem é enviada a um tópico de erro exclusivo do serviço:

| Consumidor | Tópico de mensagens com erro |
|---|---|
| Notificações | `freela.contrato.eventos.v1.notificacao-service.DLT` |
| Reputação | `freela.contrato.eventos.v1.reputacao-service.DLT` |
| Auditoria | `freela.contrato.eventos.v1.auditoria-service.DLT` |

O tópico mantém o payload original e os metadados de origem/erro do Kafka por 30 dias. Os tópicos têm três partições, como o tópico original. A mensagem é registrada nos logs com serviço, tópico, partição, offset e chave. Uma falha pausa temporariamente a partição afetada durante as tentativas; outras partições e os demais grupos de consumidores continuam.

Para diagnosticar ou reprocessar uma mensagem, abra o Kafka UI em `http://localhost:8090`, consulte o tópico DLT do serviço e copie o payload e a chave para o tópico original. O `eventId` é mantido no payload, então a deduplicação existente protege contra efeitos repetidos. As tentativas e o intervalo podem ser configurados com `KAFKA_RETRY_ATTEMPTS` e `KAFKA_RETRY_INTERVAL_MS`; a retenção em milissegundos é ajustável com `KAFKA_DLT_RETENTION_MS`.

## Consultas dos serviços

| Serviço | Endpoint |
|---|---|
| Notificações | `GET /api/notificacoes` |
| Reputação | `GET /api/reputacoes` |
| Auditoria | `GET /api/auditoria` |

Os endpoints estão disponíveis pelo Gateway ou diretamente na porta do serviço correspondente. Após os consumidores Kafka processarem os eventos, consulte os resultados:

```bash
curl -sS http://localhost:8080/api/notificacoes \
  -H "X-Correlation-Id: $CORRELATION_ID"

curl -sS http://localhost:8080/api/reputacoes \
  -H "X-Correlation-Id: $CORRELATION_ID"

curl -sS http://localhost:8080/api/auditoria \
  -H "X-Correlation-Id: $CORRELATION_ID"
```

## Logs e tracing

Os seis serviços exibem logs no console e também os enviam ao Loki. Acesse o Grafana em `http://localhost:3000` e selecione **Explore → Loki** para pesquisar logs por `service`, `contratoId`, `eventId` ou `correlationId`.

> Sem Promtail, os logs dos containers de infraestrutura, como PostgreSQL e Kafka, não são enviados ao Loki. Se Loki estiver indisponível, os logs das aplicações continuam disponíveis no console.

Grafana:

- URL: `http://localhost:3000`
- Login local inicial: `admin` / `admin`
- Altere `GRAFANA_ADMIN_USER` e `GRAFANA_ADMIN_PASSWORD` antes de expor o ambiente.

O tracing usa Micrometer/Brave e exporta os traces para Zipkin em `http://localhost:9411`. No Grafana, selecione **Explore → Zipkin**; a interface do Zipkin também está disponível diretamente. O contexto segue do Gateway para o serviço de contratos e, pelo header Kafka, aos consumidores.

O sampling padrão é 100%. Para alterá-lo, configure `TRACING_SAMPLING_PROBABILITY`. As URLs do Loki e Zipkin podem ser configuradas por `LOKI_URL` e `ZIPKIN_ENDPOINT`.

## Tecnologias

Java 21, Spring Boot, Spring Cloud, Spring Data JPA, PostgreSQL, Apache Kafka, Eureka, Docker Compose, Grafana, Loki e Zipkin.
