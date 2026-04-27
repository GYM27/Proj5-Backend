package aor.paj.projecto5.utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * A minha ferramenta de segurança para passwords.
 * Decidi usar o BCrypt porque ele trata do "Salting" automaticamente. 
 * Isto significa que mesmo que dois utilizadores usem a mesma password, 
 * o resultado guardado na base de dados será diferente.
 */
public class PasswordUtils {

    /**
     * Transforma uma password em texto limpo num hash seguro.
     * @param plainPassword A password que o utilizador escreveu.
     * @return O hash para guardar na base de dados.
     */
    public static String hashPassword(String plainPassword) {
        // O log_rounds define a "força" do hash. 12 é um bom equilíbrio entre segurança e performance.
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Verifica se a password escrita pelo utilizador coincide com o hash guardado.
     * @param plainPassword O que foi escrito no login.
     * @param hashedPassword O que está gravado na BD.
     * @return true se coincidirem.
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            // Se o hash na BD não estiver no formato correto (ex: passwords antigas em texto limpo), 
            // o BCrypt lança exceção. Nestes casos, barramos o acesso.
            return false;
        }
    }
}
