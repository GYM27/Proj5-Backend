package aor.paj.projecto5.dao;

import aor.paj.projecto5.utils.UserState;
import jakarta.ejb.Stateless;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.*;
import aor.paj.projecto5.entity.TokenEntity;
import aor.paj.projecto5.entity.UserEntity;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * O meu DAO para Utilizadores. 
 * É aqui que escrevo as queries SQL (JPQL) e uso a Criteria API para falar com o PostgreSQL.
 * Como herda de AbstractDao, já tenho o básico (find, persist, remove) feito.
 */
@Stateless
public class UserDao extends AbstractDao<UserEntity> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserDao() {
        super(UserEntity.class);
    }

    /**
     * Esta query é complexa! 
     * Ela vai à tabela de Tokens, vê qual é que está ativo, e traz-me o Utilizador (Owner) 
     * que está pendurado nesse token. Uso Criteria API para ser mais robusto.
     */
    public UserEntity findUserByToken(String tokenValue) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<UserEntity> query = cb.createQuery(UserEntity.class);
        Root<TokenEntity> tokenRoot = query.from(TokenEntity.class);

        // Selecionamos o Owner do Token
        query.select(tokenRoot.get("owner"));

        // Condições: Valor do token coincide e está marcado como ativo
        Predicate equalToken = cb.equal(tokenRoot.get("tokenValue"), tokenValue);
        Predicate isActive = cb.equal(tokenRoot.get("active"), true);

        query.where(cb.and(equalToken, isActive));

        try {
            return em.createQuery(query).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    // --- Consultas via Named Queries (Definidas na UserEntity) ---

    public UserEntity findUserByUsername(String username) {
        try {
            return em.createNamedQuery("User.findUserByUsername", UserEntity.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public UserEntity findUserByEmail(String email) {
        try {
            return em.createNamedQuery("User.findUserByEmail", UserEntity.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public UserEntity findUserByContact(String contact) {
        try {
            return em.createNamedQuery("User.findUserByContact", UserEntity.class)
                    .setParameter("contact", contact)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * O truque da eficiência! 
     * Em vez de andar a apagar leads uma a uma em Java, mando um comando UPDATE direto 
     * para a Base de Dados. É instantâneo, mesmo que o user tenha 1000 leads.
     */
    public void transferOwnership(UserEntity oldOwner, UserEntity newOwner) {
        // Atualiza as Leads
        em.createQuery("UPDATE LeadEntity l SET l.owner = :newOwner WHERE l.owner = :oldOwner")
                .setParameter("newOwner", newOwner)
                .setParameter("oldOwner", oldOwner)
                .executeUpdate();

        // Atualiza os Clientes
        em.createQuery("UPDATE ClientsEntity c SET c.owner = :newOwner WHERE c.owner = :oldOwner")
                .setParameter("newOwner", newOwner)
                .setParameter("oldOwner", oldOwner)
                .executeUpdate();
    }

    /**
     * Procura utilizadores filtrando pelo seu estado atual no sistema.
     * * @param state O estado desejado (PENDING, ACTIVE, DISABLED).
     * @return Uma lista de entidades {@link UserEntity} que correspondem ao estado fornecido.
     */
    public List<UserEntity> findUsersByState(UserState state) {
        try {
            return em.createQuery("SELECT u FROM UserEntity u WHERE u.state = :state", UserEntity.class)
                    .setParameter("state", state)
                    .getResultList();
        } catch (Exception e) {
            // Retorna lista vazia em caso de erro para evitar NullPointer no Bean
            return new ArrayList<>();
        }
    }

    /**
     * Procura utilizadores por Role.
     */
    public List<UserEntity> findUsersByRole(aor.paj.projecto5.utils.UserRoles role) {
        try {
            return em.createQuery("SELECT u FROM UserEntity u WHERE u.userRole = :role", UserEntity.class)
                    .setParameter("role", role)
                    .getResultList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * O fim da linha para um utilizador. 
     * Depois de o Bean garantir que os dados dele foram transferidos, eu removo 
     * a linha da tabela permanentemente.
     */
    public boolean hardDelete(Long id) {
        UserEntity userEntity = em.find(UserEntity.class, id);
        if (userEntity != null) {
            // Como o UsersBean já chamou o transferOwnership,
            // as listas de Leads e Clients já estarão vazias para este ID.
            em.remove(userEntity);
            return true;
        }
        return false;
    }
}