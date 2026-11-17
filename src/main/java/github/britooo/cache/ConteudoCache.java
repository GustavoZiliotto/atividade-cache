package github.britooo.cache;

import github.britooo.conteudo.Conteudo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Component
public class ConteudoCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConteudoCache.class);
    private static final int QUANTIDADE_DE_LOCKS = 64;

    private final ConcurrentHashMap<Long, EntradaCache> itens = new ConcurrentHashMap<>();
    private final Object[] locksPorId = new Object[QUANTIDADE_DE_LOCKS];
    private final Object lockDeCapacidade = new Object();
    private final int limite;
    private final long ttlNanos;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();

    public ConteudoCache(
            @Value("${app.cache.max-itens:5}") int limite,
            @Value("${app.cache.ttl-segundos:60}") long ttlSegundos) {
        if (limite < 1) {
            throw new IllegalArgumentException("app.cache.max-itens deve ser maior que zero");
        }
        if (ttlSegundos < 1) {
            throw new IllegalArgumentException("app.cache.ttl-segundos deve ser maior que zero");
        }
        this.limite = limite;
        this.ttlNanos = Math.multiplyExact(ttlSegundos, 1_000_000_000L);
        for (int i = 0; i < locksPorId.length; i++) {
            locksPorId[i] = new Object();
        }
    }

    public ResultadoBusca buscar(Long id, Supplier<Optional<Conteudo>> fonte) {
        long inicio = System.nanoTime();
        boolean hit = false;
        try {
            synchronized (lockPara(id)) {
                synchronized (lockDeCapacidade) {
                    EntradaCache entrada = itens.get(id);
                    if (entrada != null && !expirou(entrada, System.nanoTime())) {
                        entrada.ultimoAcessoNanos = System.nanoTime();
                        hits.increment();
                        hit = true;
                        return new ResultadoBusca(Optional.of(entrada.conteudo), true);
                    }
                    if (entrada != null) {
                        itens.remove(id, entrada);
                    }
                }

                misses.increment();
                Optional<Conteudo> conteudo = fonte.get();
                conteudo.ifPresent(valor -> armazenar(id, valor));
                return new ResultadoBusca(conteudo, false);
            }
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            LOGGER.info("Cache {} - id={} - {} ms", hit ? "HIT " : "MISS", id, duracaoMs);
        }
    }

    public void invalidar(Long id) {
        synchronized (lockPara(id)) {
            synchronized (lockDeCapacidade) {
                itens.remove(id);
            }
        }
    }

    public void limpar() {
        limparComLock(0);
    }

    public Estatisticas estatisticas() {
        long totalHits = hits.sum();
        long totalMisses = misses.sum();
        long total = totalHits + totalMisses;
        return new Estatisticas(
                itens.size(),
                limite,
                totalHits,
                totalMisses,
                evictions.sum(),
                total == 0 ? 0.0 : (double) totalHits / total);
    }

    private void armazenar(Long id, Conteudo conteudo) {
        synchronized (lockDeCapacidade) {
            while (itens.size() >= limite) {
                removerMenosRecentementeUsado();
            }
            long agora = System.nanoTime();
            itens.put(id, new EntradaCache(conteudo, agora, agora));
        }
    }

    private void removerMenosRecentementeUsado() {
        Map.Entry<Long, EntradaCache> menosRecente = null;
        for (Map.Entry<Long, EntradaCache> item : itens.entrySet()) {
            if (menosRecente == null
                    || item.getValue().ultimoAcessoNanos < menosRecente.getValue().ultimoAcessoNanos) {
                menosRecente = item;
            }
        }
        if (menosRecente != null && itens.remove(menosRecente.getKey(), menosRecente.getValue())) {
            evictions.increment();
        }
    }

    private boolean expirou(EntradaCache entrada, long agoraNanos) {
        return agoraNanos - entrada.armazenadoEmNanos >= ttlNanos;
    }

    private Object lockPara(Long id) {
        return locksPorId[Math.floorMod(id.hashCode(), locksPorId.length)];
    }

    private void limparComLock(int indice) {
        if (indice == locksPorId.length) {
            synchronized (lockDeCapacidade) {
                itens.clear();
            }
            return;
        }
        synchronized (locksPorId[indice]) {
            limparComLock(indice + 1);
        }
    }

    private static final class EntradaCache {
        private final Conteudo conteudo;
        private final long armazenadoEmNanos;
        private long ultimoAcessoNanos;

        private EntradaCache(Conteudo conteudo, long armazenadoEmNanos, long ultimoAcessoNanos) {
            this.conteudo = conteudo;
            this.armazenadoEmNanos = armazenadoEmNanos;
            this.ultimoAcessoNanos = ultimoAcessoNanos;
        }
    }

    public record ResultadoBusca(Optional<Conteudo> conteudo, boolean hit) {
    }

    public record Estatisticas(
            int tamanhoAtual,
            int limite,
            long hits,
            long misses,
            long evictions,
            double hitRatio) {
    }
}
