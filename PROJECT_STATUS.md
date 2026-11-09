# trace-log — Status do Projeto

> Lib de logging estruturado para aplicações Spring Boot, construída com foco em aprendizado: cada decisão técnica e estrutural é discutida antes de ser implementada.

Última atualização: 2026-11-09

---

## 1. Objetivo

Interceptar automaticamente os pontos de entrada da aplicação (**REST, SQS e Kafka**) e emitir **um único log estruturado por fluxo**, ao final dele, contendo:

- Dados da entrada (tipo, ponto de entrada, headers, body)
- Uma trilha ordenada de tudo que aconteceu no meio do caminho (`routeSteps`)
- Dados de saída ou erro
- Tempo total, data e nível do log

A ideia é que, com um `traceId` trafegado entre as apps via header, seja possível pesquisar no Datadog (ou similar) e ver todos os logs de todas as apps de um mesmo fluxo.

---

## 2. Como o log final deve ficar

### 2.1 Entrada REST

```json
{
  "traceId": "d813046b-8698-4100-bba9-5fd29160e7bc",
  "entryType": "REST",
  "httpMethod": "GET",
  "entryPoint": "http://localhost:8080/test",
  "requestHeaders": { "host": "...", "accept": "..." },
  "requestBody": { "body": { "nome": "abc", "valor": 100 } },
  "responseStatus": "200",
  "responseBody": { "...": "..." },
  "routeSteps": [ "..." ],
  "error": { "...": "só quando o erro borbulha até a raiz" },
  "timeSpent": 98,
  "date": "2026-04-16 23:57:56.514",
  "level": "INFO"
}
```

### 2.2 Entrada SQS / Kafka

Mesma estrutura, **sem** `httpMethod`, `requestHeaders`, `responseStatus` e `responseBody`.
`entryPoint` é o nome da fila (SQS) ou do tópico (Kafka).
`timeSpent` é medido até a finalização do processo (término normal, erro, nack ou postagem em outra fila).

### 2.3 Tipos de RouteStep

| Tipo | Campos |
|---|---|
| **HTTP** (Feign) | `type: "HTTP"`, `httpMethod`, `target`, `requestBody`, `response`, `responseStatus`, `timeSpent`, `error?` |
| **KAFKA** (producer) | `type: "KAFKA"`, `target` (tópico), `requestBody`, `correlationId`, `timeSpent`, `error?` |
| **SQS** (producer) | `type: "SQS"`, `target` (fila), `requestBody`, `timeSpent`, `error?` |
| **Método** (`@IncludeLogs`) | `methodName`, `requestBody` (parâmetros), `response` (retorno), `timeSpent`, `error?` |
| **Log solto** (`agregate-individual-logs`) | `methodName`, `status` (info/warn/error), `log` |

### 2.4 Tratamento de erros (proposta)

- **Erro em um step mapeado:** o step recebe `error` com `exceptionClass`, `message` e `statusCode` (só HTTP). Se o erro for tratado internamente, o fluxo continua e o `level` raiz pode permanecer `INFO`.
- **Erro que borbulha até a entrada:** além do `error` no step, a raiz recebe `error` com `exceptionClass`, `message`, `originStep` (índice do step de origem, base 0) e `stackTrace` (opcional), e o `level` vira `ERROR`.
- **Erro fora de step mapeado:** `error` só na raiz, sem `originStep`.
- **Stack trace** configurável via property (desligado por padrão).
- **REST:** `responseStatus` e `responseBody` devem refletir o que a app realmente devolveu (inclusive respostas de `@ControllerAdvice`/`@ExceptionHandler`) — motivo da troca do Aspect por Filter (ver seção 3).

### 2.5 Properties previstas

| Property | Padrão | Descrição |
|---|---|---|
| `trace-log.enabled` | `true` | Liga/desliga a lib |
| `trace-log.agregate-individual-logs` | `false` | Captura `log.info/warn/error` do SLF4J no `routeSteps` |
| `trace-log.include-stack-trace` | `false` | Inclui stack trace nos erros |

---

## 3. Decisões de arquitetura tomadas

| Decisão | Motivo |
|---|---|
| `LoggerModel` abstrata + `RestLoggerModel` + `AsyncLoggerModel` | Campos exclusivos do REST não devem existir no modelo SQS/Kafka (garantia em tempo de compilação). SQS e Kafka compartilham `AsyncLoggerModel` por não terem campos distintos hoje. |
| Factory via `Map<EntryTypeEnum, Function<EntryData, LoggerModel>>` | Evita `if/else`/`switch`; novo tipo de entrada = nova entrada no mapa. |
| `ObjectMapper` próprio da lib, `static final`, com `JavaTimeModule` | A lib não pode depender do `ObjectMapper` da app (pode não existir ou estar mal configurado). Formato de data `yyyy-MM-dd HH:mm:ss.SSS`. |
| `LogLevel` (enum próprio: INFO, WARN, ERROR) | Desacopla o modelo de qualquer framework de log. |
| **`OncePerRequestFilter` no lugar do `@Around` (AOP) para REST** | O Filter enxerga o status HTTP final e o body da resposta, inclusive quando vêm de `@ControllerAdvice`. O Aspect só enxerga o método do controller. |
| Aspects mantidos para **SQS e Kafka** | Entradas por `@SqsListener` / `@KafkaListener`. |
| Ciclo de vida extraído para `LoggerLifecycle` (composição, não herança) | Filter e Aspect *usam* o ciclo `start → processa → finish`; evita duplicar lógica (DRY). |
| `System.nanoTime()` para `timeSpent` | Relógio monotônico, correto para medir intervalos. |
| Suporte a chamadas assíncronas **desde o início** | Refatorar depois custaria mais caro. |
| `callId` (controle interno) separado do `traceId` (observabilidade) | `traceId` atravessa apps via header `X-Trace-Id`; `callId` serve só para localizar contexto/Phaser dentro da própria app. |
| `InheritableThreadLocal` + `Map<callId, LoggerModel>` (`ConcurrentHashMap`) | Threads filhas (`@Async`) herdam o `callId` e encontram o modelo correto. |
| Singleton via `enum` (`RequestContextHolderEnum`) | Forma mais segura de Singleton em Java; o `InheritableThreadLocal` fica encapsulado nele. |
| `Phaser` por fluxo | Cada chamada externa assíncrona se registra ao iniciar e se desregistra ao terminar; o `finish()` aguarda (timeout 10s) antes de logar. |
| `finish()` assíncrono (`@Async`) | Não onerar a resposta ao cliente enquanto aguarda o Phaser. |
| Steps adicionados direto ao `LoggerModel` ao finalizar | Sem classe intermediária de serviço (`RouteSteps`) como na lib de referência. |

### Decisões de build (pom.xml da lib)

- **Sem** `spring-boot-starter-parent` como parent (é para aplicações). Usa `spring-boot-dependencies` como **BOM** no `dependencyManagement`.
- **Spring Boot 3.3.5** (o 4.0.5 tinha BOM sem `starter-aop` e mistura de versões).
- **Spring Cloud OpenFeign 4.1.4** (release train `2023.0.x`, compatível com Boot 3.3.x). A 5.0.1 puxava `spring-boot-restclient:4.0.2` e quebrava a app.
- Scopes: `spring-boot-starter-web` → `provided`; `lombok` → `provided`; `spring-cloud-starter-openfeign` → `provided`; `spring-boot-starter-aop` → `compile` (é o coração da lib); `jackson-databind` e `jackson-datatype-jsr310` → `compile`.
- `maven-compiler-plugin` com versão explícita (3.13.0) e Java 17.

---

## 4. Status atual

### ✅ Concluído

- Modelos: `LoggerModel` (abstrata), `RestLoggerModel`, `AsyncLoggerModel`, `RouteStep` (ainda classe única), `EntryData`, `LogLevel`
- `LoggerContextHolder` com factories por `EntryTypeEnum`, agora com `Map<callId, LoggerModel>` (`ConcurrentHashMap`)
- `RequestContextHolderEnum` + `LogRequestAttributes` (`InheritableThreadLocal`, `callId`)
- `LoggerLifecycle` com `start()` / `finish()` e `ObjectMapper` configurado
- Campos `date` e `timeSpent` funcionando no log raiz
- `RestEntryFilter` criado (esqueleto): gera `callId`, monta `EntryData`, chama `start`/`finish`
- Captura de `requestHeaders` no REST
- Interceptação Feign via `LoggingFeignClient` (funcional, com campos básicos)
- `pom.xml` ajustado; app de teste sobe e gera log REST com `routeSteps` HTTP
- Documento de requisitos (PDF) gerado

### 🚧 Em andamento

- Migração REST: `RestEntryAspect` → `RestEntryFilter`
- Migração para `callId` / `InheritableThreadLocal`

### ⏳ Pendências imediatas (ajustes no que já existe)

- [ ] `LoggerContextHolder.clear()` também deve chamar `RequestContextHolderEnum.get().cleanUp()` (evitar leak em pool de threads)
- [ ] `timeSpent` do `LoggerLifecycle` ainda usa `ThreadLocal` simples → guardar o início no próprio `LoggerModel` (campo transitório, `@JsonIgnore`) ou migrar para o mesmo padrão do `callId`
- [ ] `RestEntryFilter`: extrair headers uma única vez e reutilizar
- [ ] `RestEntryFilter`: chamar `finish()` dentro de `try/finally` (hoje não executa se o `doFilter` lançar exceção)
- [ ] `traceId`: usar o header `X-Trace-Id` se existir, senão gerar UUID; hoje o `LoggerModel` ainda gera um UUID aleatório próprio
- [ ] `RestEntryFilter` e `AbstractEntryAspect` com `@RequiredArgsConstructor`: conferir que os subclasses (`SqsEntryAspect`, `KafkaEntryAspect`) continuam compilando/injetando
- [ ] Remover `RestEntryAspect`, `handleOutput`, `normalizeOutput` e código comentado do `AbstractEntryAspect` quando a migração terminar
- [ ] Trocar `type: "REST"` por `"HTTP"` nos steps Feign e alinhar nomes dos campos aos requisitos (`httpMethod`, `requestBody`, `response`, `responseStatus`)

### ⏳ Próximas etapas (em ordem sugerida)

1. **Captura de body no Filter**
   - Wrapper de request que permite ler o body **antes** do `doFilter` sem perdê-lo (`ResettableStreamHttpServletRequest`, ou `ContentCachingRequestWrapper` do Spring com ajuste de fluxo — decisão em aberto)
   - Wrapper de response para capturar `responseBody` e `responseStatus` (inclusive em erro tratado por `@ControllerAdvice`)
2. **Concorrência/assíncrono**
   - `Phaser` por `callId` (criado no `start`, 1 participante)
   - Feign: `register()` ao iniciar, `arriveAndDeregister()` ao terminar
   - `finish()` com `@Async` (+ `@EnableAsync` na configuração da lib) aguardando o Phaser com timeout de 10s, depois logando e limpando os maps
3. **Hierarquia de RouteStep**
   - Interface/abstrata `RouteStep` + `HttpRouteStep`, `KafkaRouteStep`, `SqsRouteStep`, `MethodRouteStep`, `LogRouteStep`
   - `timeSpent` por step
4. **Tratamento de erros**
   - Classe `ErrorDetail` (`exceptionClass`, `message`, `statusCode?`, `originStep?`, `stackTrace?`)
   - Erro no step + erro na raiz + promoção do `level` para `ERROR`
   - Property `include-stack-trace`
5. **Producers**: steps de saída para Kafka (`correlationId`) e SQS
6. **`@IncludeLogs`**: anotação + aspecto para métodos entrarem no `routeSteps`
7. **`agregate-individual-logs`**: appender customizado do SLF4J alimentando o `routeSteps`
8. **Testar SQS e Kafka** (aspectos escritos, nunca executados)
9. **Autoconfiguração** (`spring.factories` / `AutoConfiguration.imports`) e properties (`trace-log.*`)
10. **Testes automatizados**

---

## 5. Referência: lib base do trabalho

Pontos aproveitados da lib usada como inspiração:

- `InheritableThreadLocal` para propagar contexto a threads filhas
- `callId` como chave dos maps de contexto
- `Phaser` para sincronizar chamadas externas assíncronas
- Wrapper de request/response com stream "resetável"
- Log final assíncrono (não onera o cliente)

Pontos **não** aproveitados:

- Dependência externa de `LogBuilder` (aqui o objeto final é o `LoggerModel`)
- Classe de serviço `RouteSteps` com maps próprios (aqui os steps vão direto no `LoggerModel`)

---

## 6. Pontos de atenção conhecidos

- O modelo thread-per-request (Tomcat/Spring MVC) garante que a mesma thread inicia e termina o fluxo. WebFlux e threads virtuais exigirão outra estratégia de propagação de contexto.
- `InheritableThreadLocal` só propaga na **criação** da thread filha; com *thread pools* reutilizados o valor herdado pode ficar desatualizado — avaliar `TaskDecorator` ao implementar o `@Async`.
- Bodies grandes em requests/responses aumentam consumo de memória e tamanho do log — considerar limite configurável de tamanho.
- Headers sensíveis (`Authorization`, cookies) e dados sensíveis no body devem ser mascarados antes de logar — ainda não tratado.
