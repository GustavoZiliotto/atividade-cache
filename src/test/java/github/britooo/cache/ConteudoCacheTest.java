package github.britooo.cache;

import github.britooo.conteudo.Conteudo;
import github.britooo.conteudo.ConteudoMapper;
import github.britooo.conteudo.dto.ConteudoRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConteudoCacheTest {

    private final ConteudoMapper mapper = new ConteudoMapper();

    @Test
    void deveEvictarOItemMenosRecentementeAcessado() {
        ConteudoCache cache = new ConteudoCache(2, 60);
        carregar(cache, 1L);
        carregar(cache, 2L);
        cache.buscar(1L, () -> Optional.empty());
        carregar(cache, 3L);

        assertEquals(2, cache.estatisticas().tamanhoAtual());
        assertEquals(1, cache.estatisticas().evictions());
        assertTrue(cache.buscar(1L, () -> Optional.empty()).hit());

        AtomicInteger chamadasFonte = new AtomicInteger();
        ConteudoCache.ResultadoBusca resultado = cache.buscar(2L, () -> {
            chamadasFonte.incrementAndGet();
            return Optional.of(conteudo("novo"));
        });
        assertFalse(resultado.hit());
        assertEquals(1, chamadasFonte.get());
    }

    @Test
    void deveBuscarNovamenteQuandoTtlExpirar() throws InterruptedException {
        ConteudoCache cache = new ConteudoCache(2, 1);
        carregar(cache, 1L);
        Thread.sleep(1_050);

        ConteudoCache.ResultadoBusca resultado = cache.buscar(1L, () -> Optional.of(conteudo("atualizado")));

        assertFalse(resultado.hit());
        assertEquals(2, cache.estatisticas().misses());
        assertEquals(0, cache.estatisticas().evictions());
    }

    @Test
    void deveCompartilharUmaCargaConcorrenteDoMesmoId() throws Exception {
        ConteudoCache cache = new ConteudoCache(2, 60);
        CountDownLatch cargaIniciada = new CountDownLatch(1);
        CountDownLatch liberarCarga = new CountDownLatch(1);
        AtomicInteger chamadasFonte = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> cache.buscar(1L, () -> {
                chamadasFonte.incrementAndGet();
                cargaIniciada.countDown();
                aguardar(liberarCarga);
                return Optional.of(conteudo("conteudo"));
            }));

            assertTrue(cargaIniciada.await(2, TimeUnit.SECONDS));
            var segunda = executor.submit(() -> cache.buscar(1L, () -> {
                chamadasFonte.incrementAndGet();
                return Optional.of(conteudo("duplicado"));
            }));
            liberarCarga.countDown();

            assertFalse(primeira.get(2, TimeUnit.SECONDS).hit());
            assertTrue(segunda.get(2, TimeUnit.SECONDS).hit());
        }
        assertEquals(1, chamadasFonte.get());
    }

    private void carregar(ConteudoCache cache, Long id) {
        ConteudoCache.ResultadoBusca resultado = cache.buscar(id, () -> Optional.of(conteudo("conteudo-" + id)));
        assertFalse(resultado.hit());
    }

    private Conteudo conteudo(String titulo) {
        return mapper.paraEntidade(new ConteudoRequestDTO(titulo, "corpo", "autor"));
    }

    private void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tempo esgotado aguardando teste concorrente");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Teste concorrente interrompido", e);
        }
    }
}
