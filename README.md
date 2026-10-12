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

## Visão geral da arquitetura

O Gateway recebe as chamadas HTTP e localiza os serviços de aplicação pelo Eureka. O `contrato-service` é responsável pelos dados e pelo ciclo de vida do contrato; cada serviço de domínio (`contrato`, `notificacao`, `reputacao` e `auditoria`) mantém seu próprio banco PostgreSQL. As mudanças de estado geram eventos de domínio e os serviços consumidores reagem de forma independente pelo Kafka:

```text
Cliente -> API Gateway -> contrato-service -> PostgreSQL (contratos + outbox)
                                      |
                                      v
                             Kafka: freela.contrato.eventos.v1
                              /          |           \
                   notificacao-service  reputacao-service  auditoria-service
                              \          |           /
                            PostgreSQL isolado por serviço
```

O Eureka fornece descoberta para o Gateway. Grafana, Loki e Zipkin compõem a observabilidade local: aplicações enviam logs ao Loki e traces ao Zipkin; ambos podem ser consultados pelo Grafana.

| Componente de infraestrutura | Uso | Porta local |
|---|---|---:|
| PostgreSQL | Banco separado por serviço | 5432 |
| Kafka (KRaft) | Transporte dos eventos | 9092 |
| Kafka UI | Inspeção de tópicos e mensagens | 8090 |
| Grafana | Exploração de logs e traces | 3000 |
| Loki | Armazenamento centralizado de logs | 3100 |
| Zipkin | Armazenamento e consulta de traces | 9411 |

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
O Compose também inicia Grafana (`http://localhost:3000`), Loki e Zipkin (`http://localhost:9411`); suas fontes de dados Loki e Zipkin são provisionadas no Grafana. O script `infra/postgres/init-databases.sql` cria os bancos `contrato_db`, `notificacao_db`, `reputacao_db` e `auditoria_db` na primeira inicialização do volume PostgreSQL.

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
- `data` contém os dados de domínio específicos do evento. Nos quatro eventos atuais, inclui `clienteId` e `freelancerId` (UUIDs), `titulo` (texto) e `valor` (número decimal JSON). O formato `BigDecimal` de `valor` não deve ser tratado como texto.

| Evento | Campos em `data` |
|---|---|
| `ContratoCriado` | `clienteId`, `freelancerId`, `titulo`, `valor` |
| `EntregaRegistrada` | `clienteId`, `freelancerId`, `titulo`, `valor` |
| `ContratoConcluido` | `clienteId`, `freelancerId`, `titulo`, `valor` |
| `ContratoCancelado` | `clienteId`, `freelancerId`, `titulo`, `valor` |

Os UUIDs são serializados como strings e `occurredAt` como timestamp ISO-8601 UTC. Os campos comuns do envelope identificam o evento e o agregado; informações específicas ficam no objeto `data`.

A chave `contratoId` mantém os eventos de um mesmo contrato na mesma partição e preserva sua ordem. Partições diferentes podem ser processadas em paralelo. A concorrência padrão de cada consumidor é três e pode ser alterada com `KAFKA_CONSUMER_CONCURRENCY`.

Os consumidores usam `eventId` para evitar efeitos duplicados. Notificações usam também o destinatário na chave, para permitir uma notificação por participante. Auditoria e reputação persistem o identificador processado junto com seus efeitos.

O tópico pode ser alterado com `CONTRATO_EVENTOS_TOPIC`.

### Publicação transacional e idempotência

O `contrato-service` usa o padrão **Transactional Outbox**: grava a alteração do contrato e a mensagem serializada na tabela `contrato_event_outbox` dentro da mesma transação PostgreSQL. Um publicador agendado consulta até 100 mensagens pendentes a cada segundo, publica no Kafka e marca cada mensagem como publicada após confirmação do broker. Assim, uma falha antes do commit não deixa o contrato alterado sem o evento correspondente na outbox; falhas de publicação permitem nova tentativa.

Essa é uma transação entre o banco de contratos e a outbox, não uma transação distribuída entre PostgreSQL e Kafka nem uma garantia de entrega exatamente uma vez. Se o Kafka aceitar a mensagem e o processo falhar antes de persistir `publishedAt`, a mensagem pode ser publicada de novo. O produtor configura `acks=all` e `enable.idempotence=true`, mas os consumidores também precisam deduplicar para cobrir a repetição entre tentativas e reinícios.

A deduplicação é persistente no banco de cada consumidor: notificações usam a combinação `eventId` e `destinatarioId` (uma mensagem pode gerar notificações para duas pessoas); auditoria mantém `eventId` único; reputação registra `eventId` processado antes de atualizar o agregado. O registro de idempotência e o efeito do evento são gravados na mesma transação local do consumidor. O processamento é, portanto, tolerante à entrega repetida, sem alegar atomicidade global entre os serviços.

O consumidor confirma offsets por registro após processar com sucesso; falhas são repetidas e depois encaminhadas à DLT conforme descrito abaixo. Os grupos independentes permitem que cada serviço mantenha seu próprio progresso.

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

Os seis serviços exibem logs no console e também os enviam diretamente ao Loki usando o appender Logback Loki4j; não é necessário Promtail para logs das aplicações. Os logs incluem nome do serviço, nível, trace ID e span ID, além dos campos de contexto estruturados usados pelos serviços. Acesse o Grafana em `http://localhost:3000` e selecione **Explore → Loki** para pesquisar logs por `service`, `contratoId`, `eventId` ou `correlationId`. O Loki está configurado com retenção de sete dias.

> Os logs dos containers de infraestrutura, como PostgreSQL e Kafka, não são enviados ao Loki. Se Loki estiver indisponível, os logs das aplicações continuam disponíveis no console.

Grafana:

- URL: `http://localhost:3000`
- Login local inicial: `admin` / `admin`
- Altere `GRAFANA_ADMIN_USER` e `GRAFANA_ADMIN_PASSWORD` antes de expor o ambiente.

O tracing usa Micrometer/Brave e exporta os traces para Zipkin em `http://localhost:9411` (endpoint de exportação `/api/v2/spans`). A probabilidade de amostragem padrão é `1.0` (100%) e é configurável por `TRACING_SAMPLING_PROBABILITY`. No Grafana, selecione **Explore → Zipkin**; a interface do Zipkin também está disponível diretamente. O contexto HTTP é propagado pelo Gateway. Ao criar uma mensagem, o `contrato-service` persiste o contexto W3C `traceparent` validado na outbox e o inclui como header Kafka para correlação no fluxo assíncrono. `X-Correlation-Id` é um identificador funcional/log separado do contexto de tracing; se não for enviado, o Gateway/serviço gera um identificador.

As URLs do Loki e Zipkin podem ser configuradas por `LOKI_URL` e `ZIPKIN_ENDPOINT`.

## Tecnologias

Java 21, Spring Boot, Spring Cloud, Spring Data JPA, PostgreSQL, Apache Kafka, Eureka, Docker Compose, Grafana, Loki e Zipkin.
