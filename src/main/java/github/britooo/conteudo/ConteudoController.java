package github.britooo.conteudo;

import github.britooo.cache.ConteudoCache;
import github.britooo.conteudo.dto.ConteudoRequestDTO;
import github.britooo.conteudo.dto.ConteudoResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class ConteudoController {

    private final ConteudoService service;
    private final ConteudoMapper mapper;

    public ConteudoController(ConteudoService service, ConteudoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/conteudos/{id}")
    public ResponseEntity<ConteudoResponseDTO> buscar(@PathVariable Long id) {
        ConteudoCache.ResultadoBusca resultado = service.buscarPorId(id);
        String statusCache = resultado.hit() ? "HIT" : "MISS";
        return ResponseEntity.ok()
                .header("X-Cache", statusCache)
                .body(mapper.paraResposta(resultado.conteudo().orElseThrow()));
    }

    @PostMapping("/conteudos")
    public ResponseEntity<ConteudoResponseDTO> criar(@RequestBody ConteudoRequestDTO dto) {
        Conteudo criado = service.criar(mapper.paraEntidade(dto));
        return ResponseEntity.created(URI.create("/conteudos/" + criado.getId()))
                .body(mapper.paraResposta(criado));
    }

    @PutMapping("/conteudos/{id}")
    public ResponseEntity<ConteudoResponseDTO> atualizar(
            @PathVariable Long id,
            @RequestBody ConteudoRequestDTO dto) {
        Conteudo atualizado = service.atualizar(id, mapper.paraEntidade(dto));
        return ResponseEntity.ok(mapper.paraResposta(atualizado));
    }

    @DeleteMapping("/conteudos/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
