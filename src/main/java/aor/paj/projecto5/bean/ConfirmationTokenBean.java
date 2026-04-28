package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.ConfirmationTokenDao;
import aor.paj.projecto5.entity.ConfirmationTokenEntity;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EJB Stateless responsável pela gestão dos tokens de registo.
 * Revertido para a lógica estável que funcionava anteriormente.
 */
@Stateless
public class ConfirmationTokenBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ConfirmationTokenDao tokenDao;

    /**
     * Gera um token único associado a um email.
     * Uso o merge para garantir que se o email já tiver um token, ele é apenas atualizado.
     */
    public String createTokenForEmail(String email) {
        String tokenString = UUID.randomUUID().toString();
        
        // Tenta encontrar um token existente para este email
        ConfirmationTokenEntity tokenEntity = tokenDao.findTokenByEmail(email);
        
        if (tokenEntity != null) {
            // Se já existe, atualizamos o token e a validade
            tokenEntity.setToken(tokenString);
            tokenEntity.setExpiresAt(LocalDateTime.now().plusHours(24));
            tokenDao.merge(tokenEntity);
        } else {
            // Se não existe, criamos um novo
            tokenEntity = new ConfirmationTokenEntity(tokenString, email, 24);
            tokenDao.persist(tokenEntity);
        }

        return tokenString;
    }

    public ConfirmationTokenEntity validateToken(String tokenString) {
        ConfirmationTokenEntity tokenEntity = tokenDao.findTokenByString(tokenString);
        if (tokenEntity == null) return null;
        if (tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) return null;
        return tokenEntity;
    }

    public void deleteToken(ConfirmationTokenEntity tokenEntity) {
        ConfirmationTokenEntity managedToken = tokenDao.find(tokenEntity.getId());
        if (managedToken != null) {
            tokenDao.remove(managedToken);
        }
    }
}