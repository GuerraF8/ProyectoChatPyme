import java.io.Serializable;
import java.time.LocalDateTime;

public class Usuario implements Serializable {
    private String nombreUsuario;
    private String nombreCompleto;
    private String rut;
    private String correo;
    private String contrasena;
    private String perfil;
    private boolean necesitaCambiarContrasena;
    private String area; 

    // Estadísticas de uso
    private LocalDateTime horaConexion;
    private LocalDateTime horaDesconexion;
    private long mensajesEnviados;

    
    public Usuario(String nombreUsuario, String nombreCompleto, String rut, String correo, String contrasena,
            String perfil, boolean necesitaCambiarContrasena, String area) {
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.rut = rut;
        this.correo = correo;
        this.contrasena = contrasena;
        this.perfil = perfil;
        this.necesitaCambiarContrasena = necesitaCambiarContrasena;
        this.mensajesEnviados = 0;
        this.area = area;
    }

    
    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getRut() {
        return rut;
    }

    public String getCorreo() {
        return correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getPerfil() {
        return perfil;
    }

    public boolean isNecesitaCambiarContrasena() {
        return necesitaCambiarContrasena;
    }

    public void setNecesitaCambiarContrasena(boolean necesitaCambiarContrasena) {
        this.necesitaCambiarContrasena = necesitaCambiarContrasena;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    // Métodos para estadísticas
    public void setHoraConexion(LocalDateTime horaConexion) {
        this.horaConexion = horaConexion;
    }

    public void setHoraDesconexion(LocalDateTime horaDesconexion) {
        this.horaDesconexion = horaDesconexion;
    }

    public LocalDateTime getHoraConexion() {
        return horaConexion;
    }

    public LocalDateTime getHoraDesconexion() {
        return horaDesconexion;
    }

    public void incrementarMensajesEnviados() {
        this.mensajesEnviados++;
    }

    public long getMensajesEnviados() {
        return mensajesEnviados;
    }
}
