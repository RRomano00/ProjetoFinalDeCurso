package br.com.faitec.falacidade.port.service.email;

import br.com.faitec.falacidade.domain.UserModel;

public interface EmailService {
    /** Envia o código de recuperação de senha (o mesmo que a tela pede de volta). */
    void sendPasswordResetEmail(String toEmail, String code);
    void sendWelcomeEmail(String toEmail, String fullname);

    /**
     * Avisa a pessoa de que um administrador criou uma conta de Funcionário ou
     * Administrador para ela. O município vai no corpo porque é ele que define
     * quais ocorrências a conta enxerga.
     */
    void sendStaffWelcomeEmail(String toEmail, String fullname, UserModel.UserRole role, String city);
    /** Envia o código de verificação em duas etapas (MFA por e-mail). */
    void sendMfaCodeEmail(String toEmail, String code);
    void sendMfaDeactivationEmail(String toEmail, String code);
    /** Notifica a equipe (toEmail) sobre uma nova mensagem do formulário de contato. */
    void sendContactNotification(String toEmail, String senderName, String senderEmail,
                                 String subject, String message);

    /** Confirma o registro da ocorrência e agradece o comprometimento com a cidade. */
    void sendOccurrenceCreatedEmail(String toEmail, String fullname, String protocol, String title);

    /** Notifica o autor sobre a mudança de status da ocorrência (com mensagem opcional do funcionário). */
    void sendStatusChangeEmail(String toEmail, String fullname, String protocol,
                               String newStatus, String message);
}
