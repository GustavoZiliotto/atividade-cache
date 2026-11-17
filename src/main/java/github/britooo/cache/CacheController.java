package github.britooo.cache;

import github.britooo.cache.ConteudoCache.Estatisticas;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cache")
public class CacheController {

    private final ConteudoCache cache;

    public CacheController(ConteudoCache cache) {
        this.cache = cache;
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<Estatisticas> estatisticas() {
        return ResponseEntity.ok(cache.estatisticas());
    }

    @DeleteMapping
    public ResponseEntity<Void> limpar() {
        cache.limpar();
        return ResponseEntity.noContent().build();
    }
}
