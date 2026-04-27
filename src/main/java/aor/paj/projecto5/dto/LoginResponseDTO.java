package aor.paj.projecto5.dto;

import aor.paj.projecto5.utils.UserRoles;

public class LoginResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private UserRoles userRole;
    private String token;
    private String photoUrl;

    public LoginResponseDTO(Long id, String firstName, String lastName, String username, String email, UserRoles userRole, String token, String photoUrl) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.email = email;
        this.userRole = userRole;
        this.token = token;
        this.photoUrl = photoUrl;
    }

    public LoginResponseDTO(){}

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRoles getUserRole() {
        return userRole;
    }

    public String getToken() {
        return token;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setUserRole(UserRoles userRole) {
        this.userRole = userRole;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
