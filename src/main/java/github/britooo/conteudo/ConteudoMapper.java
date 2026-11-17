package github.britooo.conteudo;

import github.britooo.conteudo.dto.ConteudoRequestDTO;
import github.britooo.conteudo.dto.ConteudoResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ConteudoMapper {

    public Conteudo paraEntidade(ConteudoRequestDTO dto) {
        Conteudo conteudo = new Conteudo();
        conteudo.setTitulo(dto.titulo());
        conteudo.setCorpo(dto.corpo());
        conteudo.setAutor(dto.autor());
        return conteudo;
    }

    public ConteudoResponseDTO paraResposta(Conteudo conteudo) {
        return new ConteudoResponseDTO(
                conteudo.getId(),
                conteudo.getTitulo(),
                conteudo.getCorpo(),
                conteudo.getAutor(),
                conteudo.getAtualizadoEm());
    }
}
