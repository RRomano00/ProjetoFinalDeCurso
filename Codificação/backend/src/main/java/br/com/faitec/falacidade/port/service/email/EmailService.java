package br.com.faitec.falacidade.port.service.email;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
    void sendWelcomeEmail(String toEmail, String fullname);
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
