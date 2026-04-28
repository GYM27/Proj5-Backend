package aor.paj.projecto5.dao;

import aor.paj.projecto5.entity.ConfirmationTokenEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.NoResultException;

import java.io.Serializable;

/**
 * Data Access Object (DAO) para a entidade ConfirmationTokenEntity.
 */
@Stateless
public class ConfirmationTokenDao extends AbstractDao<ConfirmationTokenEntity> implements Serializable {

    private static final long serialVersionUID = 1L;

    public ConfirmationTokenDao() {
        super(ConfirmationTokenEntity.class);
    }

    /**
     * Procura um token pela sua string UUID.
     */
    public ConfirmationTokenEntity findTokenByString(String tokenString) {
        try {
            return em.createQuery(
                            "SELECT t FROM ConfirmationTokenEntity t WHERE t.token = :token",
                            ConfirmationTokenEntity.class)
                    .setParameter("token", tokenString)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Procura um token pelo endereço de email associado.
     */
    public ConfirmationTokenEntity findTokenByEmail(String email) {
        try {
            return em.createQuery(
                            "SELECT t FROM ConfirmationTokenEntity t WHERE t.email = :email",
                            ConfirmationTokenEntity.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}