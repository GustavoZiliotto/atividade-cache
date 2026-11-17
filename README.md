# API de conteúdos com cache próprio

API REST em Spring Boot 4 e JDK 21, com persistência JPA em H2 e cache de conteúdos implementado com `ConcurrentHashMap`. Não usa `@Cacheable`, Redis, Caffeine nem outra biblioteca de cache. O cache mantém TTL, atualiza o último acesso para remoção LRU e contabiliza hits, misses e evictions.

## Executar

Configure o Maven para usar um JDK 21 ou superior e execute:

```powershell
mvn spring-boot:run
```

Configurações em `src/main/resources/application.properties`:

```properties
app.cache.max-itens=5
app.cache.ttl-segundos=60
```

O atraso de 2 segundos é aplicado apenas em buscas que precisam consultar o repositório, para que o HIT/MISS fique evidente. A entidade é convertida em DTO na controller usando `ConteudoMapper`.

## Endpoints

| Método | Rota | Resultado |
|---|---|---|
| `POST` | `/conteudos` | Cadastra `{ "titulo": "...", "corpo": "...", "autor": "..." }` |
| `GET` | `/conteudos/{id}` | Busca por id; retorna `X-Cache: HIT` ou `X-Cache: MISS` |
| `PUT` | `/conteudos/{id}` | Atualiza e invalida o item correspondente |
| `DELETE` | `/conteudos/{id}` | Exclui e invalida o item correspondente |
| `GET` | `/cache/estatisticas` | Retorna tamanho, limite, hits, misses, evictions e hit ratio |
| `DELETE` | `/cache` | Limpa os itens sem zerar os contadores |

O H2 é em memória e fica disponível no console em `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:conteudodb`, usuário `sa`, senha vazia).

## Demonstração no Postman/Insomnia

Para cobrir também o TTL sem reiniciar a aplicação (o banco H2 é em memória), inicie uma instância com TTL curto e use essa mesma instância em toda a sequência:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.cache.ttl-segundos=15"
```

1. Cadastre sete conteúdos por `POST /conteudos` e anote os IDs retornados.
2. Faça `GET /conteudos/1` duas vezes. A primeira resposta é MISS (cerca de 2 s) e a segunda é HIT (rápida). Confira o header `X-Cache` e os logs da aplicação.
3. Faça GETs para os IDs 2, 3, 4 e 5. Acesse novamente o ID 1 para torná-lo o mais recente e então consulte o ID 6. O cache cheio remove o ID 2, o menos recentemente usado. Consultar o ID 2 novamente gera MISS.
4. Atualize um conteúdo com `PUT` e consulte-o imediatamente: o cache foi invalidado e a busca seguinte gera MISS.
5. Consulte um ID que ainda não esteja no cache duas vezes, espere 16 segundos e consulte-o de novo: deve ocorrer MISS. O TTL normal permanece configurado em 60 segundos no arquivo de propriedades.
6. Consulte `GET /cache/estatisticas` ao final e capture as respostas, o tempo exibido pelo Postman/Insomnia e o console de logs.

Exemplos de logs esperados:

```text
INFO  ConteudoCache : Cache MISS - id=1 - 2015 ms
INFO  ConteudoCache : Cache HIT  - id=1 - 1 ms
```

O arquivo `demo.http` contém exemplos de requisições para executar diretamente em uma IDE compatível com HTTP Client. Os prints precisam ser capturados no Postman/Insomnia durante a execução local.

## Testes

```powershell
mvn test
```
