package github.britooo.conteudo;

import github.britooo.cache.ConteudoCache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConteudoService {

    private final ConteudoRepository repository;
    private final ConteudoCache cache;

    public ConteudoService(ConteudoRepository repository, ConteudoCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public ConteudoCache.ResultadoBusca buscarPorId(Long id) {
        ConteudoCache.ResultadoBusca resultado = cache.buscar(id, () -> {
            simularLentidaoDaFonte();
            return repository.findById(id);
        });
        if (resultado.conteudo().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conteúdo não encontrado");
        }
        return resultado;
    }

    @Transactional
    public Conteudo criar(Conteudo conteudo) {
        return repository.save(conteudo);
    }

    @Transactional
    public Conteudo atualizar(Long id, Conteudo alteracoes) {
        Conteudo conteudo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conteúdo não encontrado"));
        conteudo.setTitulo(alteracoes.getTitulo());
        conteudo.setCorpo(alteracoes.getCorpo());
        conteudo.setAutor(alteracoes.getAutor());
        Conteudo salvo = repository.save(conteudo);
        invalidarAposCommit(id);
        return salvo;
    }

    @Transactional
    public void excluir(Long id) {
        Conteudo conteudo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conteúdo não encontrado"));
        repository.delete(conteudo);
        invalidarAposCommit(id);
    }

    private void invalidarAposCommit(Long id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.invalidar(id);
            }
        });
    }

    private void simularLentidaoDaFonte() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A busca simulada foi interrompida", e);
        }
    }
}
