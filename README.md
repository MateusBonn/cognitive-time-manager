# Cognitive Time Manager — Backend MVP

Sistema de gestão de tempo cognitivo para desenvolvedores, construído sobre Arquitetura Hexagonal, Event-Driven Pipeline e Domain-Driven Design.

---

## Índice

1. [Visão Macro — Event-Driven Pipeline](#1-visão-macro--event-driven-pipeline)
2. [Visão Micro — Arquitetura Hexagonal](#2-visão-micro--arquitetura-hexagonal)
3. [Design Patterns Aplicados](#3-design-patterns-aplicados)
4. [Matemática e Regras do Motor](#4-matemática-e-regras-do-motor)
5. [Requisitos e Mapeamento](#5-requisitos-e-mapeamento)
6. [Estrutura de Pacotes](#6-estrutura-de-pacotes)
7. [Como Executar](#7-como-executar)
8. [API Reference](#8-api-reference)
9. [WebSocket Protocol](#9-websocket-protocol)
10. [O Desafio: Auditoria Humana vs. IA (Como Contribuir)](#10-o-desafio-auditoria-humana-vs-ia-como-contribuir)

---

## 1. Visão Macro — Event-Driven Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│                   EVENT-DRIVEN PIPELINE                          │
│                                                                  │
│  ┌─────────────────────┐                                         │
│  │  Injection Agent    │  Simulated by MeetingEventConsumer      │
│  │  (Teams / Google /  │  Listens to RabbitMQ and translates    │
│  │   Outlook)          │  external calendar payloads to domain   │
│  └──────────┬──────────┘  Meeting objects.                       │
│             │                                                    │
│             ▼  JSON message                                      │
│  ┌──────────────────────┐                                        │
│  │  RabbitMQ Broker     │  Topic Exchange: cognitive.exchange     │
│  │  (Lightweight)       │  Queue: cognitive.calendar.events       │
│  │                      │  DLQ: cognitive.dead-letter            │
│  └──────────┬───────────┘                                        │
│             │                                                    │
│             ▼  Domain Meeting object                             │
│  ┌──────────────────────────────────────────┐                   │
│  │  Core Engine (Spring Boot Application)   │                   │
│  │                                          │                   │
│  │  ProcessMeetingEventService              │                   │
│  │    → AllocateTimeService                 │                   │
│  │        → TimeAllocationEngine (domain)   │                   │
│  │    → HandleInterruptionService           │                   │
│  │        → InterruptionHandler (domain)    │                   │
│  │        → DeveloperState (State Pattern)  │                   │
│  │                                          │                   │
│  └──────────┬───────────────────────────────┘                   │
│             │                                                    │
│             ▼  WebSocket push                                    │
│  ┌──────────────────────┐                                        │
│  │  Developer's Client  │  Browser / IDE plugin / Desktop app   │
│  │  (WebSocket)         │  Receives STATE_CHANGE, INTERRUPTION,  │
│  │                      │  SCHEDULE_UPDATE events in real-time   │
│  └──────────────────────┘                                        │
└──────────────────────────────────────────────────────────────────┘
```

### Por que RabbitMQ e não Apache Kafka?

| Critério | RabbitMQ | Apache Kafka |
|---|---|---|
| Volume de eventos | 1–20/dia por dev | Bilhões/dia |
| Latência de entrega | < 5ms | ~10–50ms + warm-up |
| Overhead operacional | Baixo (1 nó) | Alto (ZooKeeper/KRaft + brokers) |
| Replay de mensagens | Não necessário no MVP | Fundamental para analytics |
| TTL por mensagem | Nativo | Requer configuração por tópico |
| Dead Letter Queue | Nativo | Requer consumidor dedicado |
| Custo em produção | Mínimo | Elevado (cluster mínimo 3 brokers) |

**Decisão:** Para o volume do sistema (mutações de agenda de 1 desenvolvedor = dezenas de eventos/dia), Kafka seria overengineering claro. O overhead de manter um cluster Kafka supera qualquer benefício de throughput que só se justifica em millions of events/second. RabbitMQ entrega sub-10ms com infraestrutura de 1 nó, suporta DLQ nativa e tem integração Spring AMQP de primeira classe.

---

## 2. Visão Micro — Arquitetura Hexagonal (Ports & Adapters)

```
                    ╔══════════════════════════════════════╗
                    ║          DOMAIN (Núcleo Puro)        ║
                    ║  ┌────────────────────────────────┐  ║
                    ║  │  TimeBlock · ScheduleDay       │  ║
                    ║  │  Meeting · InterruptionResult  │  ║
                    ║  │  CognitiveState · Scenario     │  ║
                    ║  │  EngineConstants               │  ║
                    ║  └────────────────────────────────┘  ║
                    ║  ┌────────────────────────────────┐  ║
                    ║  │  State Pattern                 │  ║
                    ║  │  DeveloperState (interface)    │  ║
                    ║  │  DeepWorkState                 │  ║
                    ║  │  ShallowWorkState              │  ║
                    ║  │  ForcedRestState               │  ║
                    ║  │  IdleState                     │  ║
                    ║  └────────────────────────────────┘  ║
                    ║  ┌────────────────────────────────┐  ║
                    ║  │  Strategy Pattern              │  ║
                    ║  │  AllocationStrategy (iface)    │  ║
                    ║  │  SparseScheduleStrategy        │  ║
                    ║  │  DenseScheduleStrategy         │  ║
                    ║  └────────────────────────────────┘  ║
                    ║  ┌────────────────────────────────┐  ║
                    ║  │  Engine                        │  ║
                    ║  │  TimeAllocationEngine          │  ║
                    ║  │  InterruptionHandler           │  ║
                    ║  └────────────────────────────────┘  ║
                    ╚═════╦══════════════════╦═════════════╝
                          ║ PRIMARY PORTS    ║ SECONDARY PORTS
              ┌───────────╩──────┐    ┌──────╩──────────────┐
              │ AllocateTime     │    │ ScheduleDayRepo      │
              │ HandleInterrupt  │    │ TimeBlockRepo        │
              │ ProcessMeeting   │    │ NotificationPort     │
              └───────────┬──────┘    └──────┬──────────────┘
                          │                  │
          ┌───────────────┘                  └──────────────────┐
          │ PRIMARY ADAPTERS (Inbound)                          │ SECONDARY ADAPTERS (Outbound)
          │                                                     │
  ┌───────────────┐  ┌─────────┐  ┌────────┐   ┌──────────┐  ┌────────────────┐
  │ RabbitMQ      │  │ REST    │  │ WS     │   │ JPA Repo │  │ WS Notif.      │
  │ Consumer      │  │ Ctrl    │  │ Handler│   │ Adapter  │  │ Adapter        │
  │               │  │         │  │        │   │ H2/PG    │  │                │
  └───────────────┘  └─────────┘  └────────┘   └──────────┘  └────────────────┘
```

### Princípio Fundamental — RNF03

O núcleo do domínio (`com.cognitivemanager.domain.*`) contém **zero** anotações de framework:
- Nenhum `@Service`, `@Component`, `@Repository`
- Nenhum `@Entity`, `@Column`, `@Table` (Jakarta Persistence)
- Nenhum `@JsonProperty` (Jackson)

O domínio é **testável sem Spring**, sem banco de dados, sem RabbitMQ. Um teste unitário de `TimeAllocationEngine` instancia a classe diretamente com `new TimeAllocationEngine()`.

---

## 3. Design Patterns Aplicados

### 3.1 State Pattern
**Localização:** `com.cognitivemanager.domain.state`

**Problema resolvido:** O comportamento ao receber uma interrupção de reunião é fundamentalmente diferente dependendo do estado cognitivo atual. Sem o State Pattern, o `InterruptionHandler` conteria um `if (state == DEEP_WORK) { ... } else if (state == SHALLOW_WORK) { ... }` que violaria o Open/Closed Principle.

**Implementação:**
```
DeveloperState (interface)
├── DeepWorkState   ← contém a lógica dos 3 cenários (§4)
├── ShallowWorkState ← interrupções são absorvidas sem custo
├── ForcedRestState  ← descanso obrigatório; só reunião urgente interrompe
└── IdleState        ← sem bloco ativo; pronto para alocação
```

**Por que State e não Strategy aqui?** Porque o objeto *muda de comportamento baseado em seu estado interno*, e as transições entre estados são parte da lógica de negócio (ex: `ForcedRestState.transition(now)` retorna `IdleState` quando o descanso termina).

**Onde aparece nos comentários:** Cabeçalho de cada classe em `domain/state/`.

---

### 3.2 Strategy Pattern
**Localização:** `com.cognitivemanager.domain.strategy`

**Problema resolvido:** O algoritmo de alocação de tempo muda baseado na densidade da agenda do dia. Em dias esparsos, priorizamos descanso máximo (R_max). Em dias densos, comprimimos o descanso (R_min) para maximizar blocos de Deep Work nos intervalos fragmentados.

**Implementação:**
```
AllocationStrategy (interface)
├── SparseScheduleStrategy  ← < 3 reuniões OU < 120 min de reuniões → usa R_max
└── DenseScheduleStrategy   ← ≥ 3 reuniões E ≥ 120 min de reuniões → usa R_min
```

**Seleção em runtime:** `TimeAllocationEngine.selectStrategy()` percorre a lista de estratégias registradas e retorna a primeira aplicável — tornando trivial adicionar `TravelDayStrategy`, `SprintStrategy`, etc.

**Onde aparece nos comentários:** Cabeçalho de `AllocationStrategy`, ambas as implementações, e `TimeAllocationEngine`.

---

### 3.3 Adapter Pattern
**Localização:** `com.cognitivemanager.adapter.*`

**Problema resolvido:** Isolar o domínio de detalhes de infraestrutura — payloads externos de calendário, serialização JSON, JPA, WebSocket.

**Implementações:**

| Adapter | Direção | Traduz |
|---|---|---|
| `MeetingEventConsumer` | Inbound | JSON RabbitMQ payload → `Meeting` domínio |
| `TimeManagementController` | Inbound | HTTP request → use case call |
| `CognitiveStateWebSocketHandler` | Inbound | WS connection → session registry |
| `JpaScheduleDayRepository` | Outbound | `ScheduleDay` → JPA entities e vice-versa |
| `WebSocketNotificationAdapter` | Outbound | Domain events → JSON WS push |

**Onde aparece nos comentários:** Cabeçalho de cada classe em `adapter/`.

---

### 3.4 Factory Pattern
**Localização:** `com.cognitivemanager.domain.model.TimeBlock`, `InterruptionResult`, `ScheduleDay`

**Problema resolvido:** Garantir invariantes de negócio na criação de objetos de domínio. Construtor privado + factory methods nomeados evitam estados inválidos.

**Implementações:**
```java
// TimeBlock — dois factories com semântica diferente
TimeBlock.create(startTime, durationMinutes, state)           // bloco normal
TimeBlock.createInterrupted(startTime, actualMinutes, state)  // bloco cortado

// InterruptionResult — factories semânticos por cenário
InterruptionResult.emergencyCooldown(meeting, blocks, window)
InterruptionResult.decisionRequired(meeting, blocks, window)
InterruptionResult.safeAutoTruncation(meeting, blocks, window)

// ScheduleDay — factories com propósitos distintos
ScheduleDay.createEmpty(developerId, date)
ScheduleDay.reconstitute(id, date, developerId, blocks, meetings)
```

**Onde aparece nos comentários:** Cabeçalho de cada classe e nos próprios métodos.

---

## 4. Matemática e Regras do Motor

### Constantes Biológicas

| Constante | Valor | Descrição |
|---|---|---|
| `D_min` | 85 min | Mínimo para atingir estado de flow |
| `D_max` | 125 min | Máximo antes da degradação cognitiva |
| `R_min` | 15 min | Descanso mínimo (dias densos) |
| `R_max` | 25 min | Descanso máximo (dias esparsos) |
| `L_max` | 250 min | Limite cumulativo diário de Deep Work |
| `EMERGENCY_THRESHOLD` | 25 min | Limiar para cool-down de emergência |
| `SAFE_THRESHOLD` | 105 min | Limiar para auto-truncamento seguro |
| `PRE_MEETING_REST` | 20 min | Descanso obrigatório pré-reunião |

### Algoritmo de Distribuição (Recursivo)

```
allocate(windowStart, T_livre, accumulated):

  SE accumulated ≥ L_max:
    → retorna [SHALLOW_WORK(windowStart, T_livre)]

  SE T_livre < D_min:
    → retorna [SHALLOW_WORK(windowStart, T_livre)]

  // Passo 3: alocar Deep Work
  remaining_capacity = L_max - accumulated
  D_alocado = min(T_livre, min(D_max, remaining_capacity))

  // Decisão de descanso (Sparse vs Dense Strategy)
  remainder = T_livre - D_alocado
  critical_space = remainder < (R_max + D_min)   // Sparse
  R_alocado = critical_space ? min(R_min, remainder)
                             : min(R_max, remainder)

  T_novo = T_livre - D_alocado - R_alocado

  retorna [
    DEEP_WORK(windowStart, D_alocado),
    FORCED_REST(windowStart + D_alocado, R_alocado),
    ...allocate(windowStart + D_alocado + R_alocado, T_novo, accumulated + D_alocado)
  ]
```

**Exemplo — dia esparso, janela de 4h (240 min), acumulado = 0:**

| Iteração | T_livre | D_alocado | R_alocado | Acumulado | Bloco |
|---|---|---|---|---|---|
| 1ª | 240 | 125 | 25 | 0→125 | DEEP(125) + REST(25) |
| 2ª | 90 | 85 | 5 | 125→210 | DEEP(85) + REST(5) |
| 3ª | 0 | — | — | 210 | — |

**Exemplo — limite atingido (acumulado = 250, janela = 60 min):**
→ Retorna `[SHALLOW_WORK(60)]` diretamente.

### Regras de Interrupção por Reunião Urgente

Dado `Janela = M_start - now`:

```
SE Janela ≤ 25 min  →  CENÁRIO 1: EMERGENCY_COOLDOWN
  1. Registra Deep Work interrompido (durationMinutes = elapsed desde blockStart)
  2. Bloqueia em FORCED_REST até M_start
  3. Reunião = SHALLOW_WORK

SE 25 < Janela < 105 min  →  CENÁRIO 2: DECISION_REQUIRED
  Opção A (SWITCH_TO_SHALLOW):
    → SHALLOW_WORK(now, Janela) + SHALLOW_WORK(M_start, meeting_duration)
  Opção B (PERSIST_DEEP_WORK):
    → DEEP_WORK(now, Janela - 20) + FORCED_REST(M_start - 20, 20) + SHALLOW_WORK(M_start, ...)

SE Janela ≥ 105 min  →  CENÁRIO 3: SAFE_AUTO_TRUNCATION
  → DEEP_WORK(now, Janela - 20) + FORCED_REST(M_start - 20, 20) + SHALLOW_WORK(M_start, ...)
  (sem decisão do desenvolvedor — auto-aplicado)
```

---

## 5. Requisitos e Mapeamento

| RF | Descrição | Implementação |
|---|---|---|
| RF01 | Consumir eventos de calendário em tempo real | `MeetingEventConsumer` → RabbitMQ `@RabbitListener` |
| RF02 | Calcular janelas de tempo livre recursivamente | `TimeAllocationEngine.processDay()` + strategies |
| RF03 | Limitar Deep Work cumulativo diário (≤ 250 min) | Base case 1 em ambas as strategies; `L_max` em `EngineConstants` |
| RF04 | Recalcular e propor opções em interrupções urgentes | `HandleInterruptionService` → `DeepWorkState.scenarioTwo_DecisionWindow()` |
| RF05 | Forçar descanso imediato se janela ≤ 25 min | `DeepWorkState.scenarioOne_EmergencyCooldown()` |

| RNF | Descrição | Implementação |
|---|---|---|
| RNF01 | Latência de cálculo < 50ms | Engine em memória; medido em `AllocateTimeService` (log em ms) |
| RNF02 | SOLID + acoplamento mínimo | Ports & Adapters; dependency injection via construtores |
| RNF03 | Sem anotações Spring/JPA no domínio | `domain.*` contém apenas Java puro + imports da própria camada |

---

## 6. Estrutura de Pacotes

```
src/main/java/com/cognitivemanager/
│
├── CognitiveTimeManagerApplication.java        ← Entry point
│
├── domain/                                      ← NÚCLEO PURO (sem framework)
│   ├── constants/
│   │   └── EngineConstants.java                ← D_min, D_max, R_min, R_max, L_max, thresholds
│   ├── model/
│   │   ├── CognitiveState.java                 ← Enum: DEEP_WORK, SHALLOW_WORK, FORCED_REST, IDLE
│   │   ├── TimeBlock.java                      ← [Factory Pattern] Bloco imutável de tempo
│   │   ├── ScheduleDay.java                    ← [Factory Pattern] Aggregate root do dia
│   │   ├── Meeting.java                        ← Value object de reunião
│   │   ├── InterruptionScenario.java           ← Enum: 3 cenários de interrupção
│   │   └── InterruptionResult.java             ← [Factory Pattern] Resultado de interrupção
│   ├── state/                                  ← [State Pattern]
│   │   ├── DeveloperState.java                 ← Interface do State Pattern
│   │   ├── DeepWorkState.java                  ← Lógica dos 3 cenários de interrupção
│   │   ├── ShallowWorkState.java
│   │   ├── ForcedRestState.java
│   │   └── IdleState.java
│   ├── strategy/                               ← [Strategy Pattern]
│   │   ├── AllocationStrategy.java             ← Interface do Strategy Pattern
│   │   ├── SparseScheduleStrategy.java         ← Dias com < 3 reuniões → R_max
│   │   └── DenseScheduleStrategy.java          ← Dias com ≥ 3 reuniões → R_min
│   └── engine/
│       ├── TimeAllocationEngine.java           ← Orquestrador recursivo (usa Strategy Pattern)
│       └── InterruptionHandler.java            ← Delega ao DeveloperState (usa State Pattern)
│
├── port/                                        ← FRONTEIRAS HEXAGONAIS
│   ├── in/                                     ← Primary Ports (Driver)
│   │   ├── AllocateTimeUseCase.java
│   │   ├── HandleInterruptionUseCase.java
│   │   └── ProcessMeetingEventUseCase.java
│   └── out/                                    ← Secondary Ports (Driven)
│       ├── ScheduleDayRepository.java
│       ├── TimeBlockRepository.java
│       └── NotificationPort.java
│
├── application/                                 ← SERVIÇOS DE APLICAÇÃO (orquestração)
│   ├── AllocateTimeService.java
│   ├── HandleInterruptionService.java
│   └── ProcessMeetingEventService.java
│
├── adapter/                                     ← ADAPTADORES (infraestrutura)
│   ├── in/                                     ← Primary Adapters (Inbound)
│   │   ├── messaging/
│   │   │   ├── MeetingEventConsumer.java       ← [Adapter Pattern] RabbitMQ → Domain
│   │   │   └── MeetingEventPayload.java        ← DTO externo de calendário
│   │   ├── rest/
│   │   │   ├── TimeManagementController.java   ← [Adapter Pattern] HTTP → Use Cases
│   │   │   └── dto/
│   │   │       ├── ScheduleDayResponse.java
│   │   │       └── InterruptionDecisionRequest.java
│   │   └── websocket/
│   │       └── CognitiveStateWebSocketHandler.java
│   └── out/                                    ← Secondary Adapters (Outbound)
│       ├── persistence/
│       │   ├── ScheduleDayJpaEntity.java
│       │   ├── TimeBlockJpaEntity.java
│       │   ├── SpringDataScheduleDayRepository.java
│       │   └── JpaScheduleDayRepository.java   ← [Adapter Pattern] Domain → JPA
│       └── notification/
│           └── WebSocketNotificationAdapter.java ← [Adapter Pattern] Domain → WebSocket
│
└── config/
    ├── RabbitMQConfig.java                     ← Exchange, queues, DLQ, JSON converter
    └── WebSocketConfig.java                    ← Endpoint registration
```

---

## 7. Como Executar

### Pré-requisitos
- Java 21+
- Maven 3.9+
- RabbitMQ (Docker recomendado)

### Iniciar RabbitMQ

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

Management UI: http://localhost:15672 (guest/guest)

### Executar a aplicação

```bash
# Clonar e entrar no diretório
cd cognitive-time-manager

# Compilar e executar
mvn spring-boot:run

# Ou com variáveis explícitas
RABBITMQ_HOST=localhost RABBITMQ_PORT=5672 mvn spring-boot:run
```

### Acessar o H2 Console (banco em memória)
http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:cognitivedb`
- Username: `sa` / Password: (vazio)

---

## 8. API Reference

### GET `/api/v1/schedule/{developerId}/{date}`
Retorna o schedule do dia (aloca se ainda não existir).

```bash
curl http://localhost:8080/api/v1/schedule/dev-42/2026-07-14
```

### POST `/api/v1/schedule/{developerId}/reallocate`
Re-calcula o schedule do dia (útil após mudanças na agenda).

```bash
curl -X POST "http://localhost:8080/api/v1/schedule/dev-42/reallocate?date=2026-07-14"
```

### POST `/api/v1/schedule/{developerId}/interrupt`
Simula uma reunião urgente (testa os 3 cenários de interrupção).

```bash
curl -X POST http://localhost:8080/api/v1/schedule/dev-42/interrupt \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Sync urgente com PM",
    "start_time": "2026-07-14T10:20:00",
    "end_time":   "2026-07-14T11:00:00"
  }'
```

### POST `/api/v1/schedule/{developerId}/decision`
Aplica a decisão do desenvolvedor em um cenário `DECISION_REQUIRED`.

```bash
# Opção A: mudar para Shallow Work
curl -X POST http://localhost:8080/api/v1/schedule/dev-42/decision \
  -H "Content-Type: application/json" \
  -d '{"meeting_id": "<uuid>", "switch_to_shallow": true}'

# Opção B: continuar Deep Work (descanso automático aos M_start - 20)
curl -X POST http://localhost:8080/api/v1/schedule/dev-42/decision \
  -H "Content-Type: application/json" \
  -d '{"meeting_id": "<uuid>", "switch_to_shallow": false}'
```

### Publicar evento via RabbitMQ (simular Injection Agent)

```bash
# Publicar uma reunião urgente via Management API
curl -u guest:guest -X POST http://localhost:15672/api/exchanges/%2F/cognitive.exchange/publish \
  -H "Content-Type: application/json" \
  -d '{
    "routing_key": "calendar.event.created",
    "payload": "{\"event_id\":\"550e8400-e29b-41d4-a716-446655440001\",\"title\":\"Sprint Review\",\"start_time\":\"2026-07-14T15:00:00\",\"end_time\":\"2026-07-14T16:00:00\",\"source\":\"TEAMS\",\"priority\":\"URGENT\",\"developer_id\":\"dev-42\",\"event_type\":\"CREATED\"}",
    "payload_encoding": "string",
    "properties": {"content_type": "application/json"}
  }'
```

---

## 9. WebSocket Protocol

Conectar em: `ws://localhost:8080/ws/cognitive/{developerId}`

O servidor envia três tipos de mensagens JSON:

### STATE_CHANGE
```json
{
  "type": "STATE_CHANGE",
  "state": "FORCED_REST",
  "message": "EMERGENCY: Reunião 'Sync urgente' em 18 min. Deep Work interrompido.",
  "timestamp": "2026-07-14T09:47:00"
}
```

### INTERRUPTION
```json
{
  "type": "INTERRUPTION",
  "scenario": "DECISION_REQUIRED",
  "window_minutes": 62,
  "requires_decision": true,
  "meeting_title": "Tech Review",
  "meeting_start": "2026-07-14T11:30:00",
  "message": "DECISÃO NECESSÁRIA: Reunião 'Tech Review' em 62 min. Opção A: Mudar para Shallow Work. Opção B: descanso obrigatório às 11:10.",
  "proposed_blocks": [
    { "state": "SHALLOW_WORK", "start_time": "2026-07-14T10:28:00", "end_time": "2026-07-14T11:30:00", "duration_minutes": 62 }
  ],
  "evaluated_at": "2026-07-14T10:28:00"
}
```

### SCHEDULE_UPDATE
```json
{
  "type": "SCHEDULE_UPDATE",
  "date": "2026-07-14",
  "cumulative_deep_work_minutes": 125,
  "remaining_capacity_minutes": 125,
  "daily_limit_reached": false,
  "block_count": 5,
  "meeting_count": 2,
  "timestamp": "2026-07-14T09:00:00"
}
```

---

## Decisões Técnicas Adicionais

**Java Records para DTOs:** `ScheduleDayResponse`, `InterruptionDecisionRequest` usam Java 21 records — imutáveis, sem boilerplate, semântica de valor.

**Aggregate Root pattern:** `ScheduleDay` é o único ponto de entrada para persistência. `TimeBlock`s são salvas através do aggregate, nunca diretamente — garantindo consistência.

**Imutabilidade no domínio:** Todos os objetos de domínio são imutáveis. Mutações retornam novas instâncias (`day.withBlocks(...)`, `day.withMeeting(...)`). Isso elimina bugs de estado compartilhado e simplifica testes.

**Sem Lombok:** A escolha deliberada de não usar Lombok mantém o domínio completamente transparente — sem processamento de anotações em tempo de compilação, sem dependências ocultas, sem "mágica". Java 21 Records e IDE moderno tornam o boilerplate aceitável.

## 10. O Desafio: Auditoria Humana vs. IA (Como Contribuir)

A fundação arquitetural deste projeto (Ports & Adapters, DDD, limites matemáticos) foi definida por um humano, mas **100% do código foi gerado por Inteligência Artificial (Claude 3.5 Sonnet)** em zero-shot prompt.

Este repositório é um laboratório prático. A tese é: a IA escreve código sintático em segundos, mas peca em *trade-offs* de longo prazo, otimização de memória e na identificação de *edge cases* complexos. A responsabilidade final pela engenharia continua sendo humana.

Convido a comunidade a auditar as decisões da IA e provar isso na prática.

### Onde focar sua revisão (Bugs e Overengineering esperados):

1. **O Motor Recursivo (`TimeAllocationEngine.java`):** A recursão matemática para calcular as janelas de tempo livre está otimizada? Existe risco de *StackOverflow* se a agenda do desenvolvedor for bizarramente fragmentada?
2. **Overengineering:** A IA aplicou *State Pattern* e *Strategy* corretamente ou criou complexidade acidental? As abstrações são realmente necessárias?
3. **Pureza do Domínio:** A IA deixou vazar detalhes de infraestrutura ou regras de negócio implícitas para dentro dos *Adapters*?
4. **Edge Cases:** Os cálculos matemáticos sobrevivem a cenários caóticos? (ex: reuniões sobrepostas, eventos de 2 minutos).

### Como submeter seu PR (Regras do Jogo):

1. Faça o fork do projeto.
2. Crie uma branch com o prefixo da sua intenção: `refactor/algoritmo-alocacao` ou `fix/vazamento-dominio`.
3. **Na descrição do PR:** Seja analítico. Explique **qual foi o erro/decisão sub-ótima da IA** e **por que** a sua solução de engenharia humana é superior.
4. **Regra de Ouro (Hard Constraint):** Se o seu PR adicionar qualquer anotação de framework (`@Service`, `@Entity`, Jackson, etc.) dentro do pacote `domain/`, ele será sumariamente rejeitado. A pureza da Arquitetura Hexagonal é inegociável.

Abra uma issue para discutir arquitetura ou mande o PR diretamente. Mostre onde a máquina falhou.