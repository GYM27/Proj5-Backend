package aor.paj.projecto5.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

/**
 * DTO específico para a atualização de perfil pelo próprio utilizador.
 * Diferente do UserDTO, aqui a password é OPCIONAL (só muda se preenchida).
 */
public class UserUpdateDTO {

    @NotBlank(message = "O primeiro nome não pode estar vazio.")
    private String firstName;

    @NotBlank(message = "O último nome não pode estar vazio.")
    private String lastName;

    @NotBlank(message = "O email não pode estar vazio.")
    @Email(message = "Email inválido.")
    private String email;

    @NotBlank(message = "O contacto não pode estar vazio.")
    @Pattern(regexp = "^[\\d\\s\\+\\-()]*$", message = "Contacto inválido.")
    private String cellphone;

    // Relaxamos a anotação @URL estrita para permitir string vazia ou formatos parciais.
    private String photoUrl;

    // Password é opcional na edição
    private String password;

    // Password atual para validação de segurança
    private String currentPassword;

    private String language;

    public UserUpdateDTO() {}

    // Getters e Setters
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCellphone() { return cellphone; }
    public void setCellphone(String cellphone) { this.cellphone = cellphone; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
