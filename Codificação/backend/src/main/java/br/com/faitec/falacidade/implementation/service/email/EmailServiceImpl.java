package br.com.faitec.falacidade.implementation.service.email;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.port.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Envio de e-mails transacionais do sistema.
 * Todos usam o mesmo layout: marca no topo, conteúdo no cartão branco e
 * rodapé escuro com os contatos e o endereço.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final String BRAND_COLOR = "#1F4E79";
    private static final String ADDRESS     = "Santa Rita do Sapucaí - MG";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.contact-recipient:fala.cidade.faitec@gmail.com}")
    private String contactEmail;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Código de recuperação de senha (válido por 30 min). */
    @Override
    @Async("emailExecutor")
    public void sendPasswordResetEmail(String toEmail, String code) {
        String text =
            "Olá!\n\n" +
            "Recebemos uma solicitação para redefinir sua senha.\n\n" +
            "Seu código de recuperação é: " + code + "\n\n" +
            "Informe esse código na tela \"Esqueci minha senha\" do aplicativo. " +
            "Ele é válido por 30 minutos.\n\n" +
            "Se você não solicitou a redefinição, ignore este e-mail.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Recuperação de senha</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá!</p>" +
            "<p style='font-size:15px; color:#333;'>Use o código abaixo na tela " +
            "<strong>Esqueci minha senha</strong> para criar uma nova senha:</p>" +
            "<div style='font-size:30px; font-weight:bold; letter-spacing:6px; color:" + BRAND_COLOR + ";" +
            "            background:#eef4fb; border-radius:8px; padding:16px; margin:20px 0; text-align:center;'>" +
                 code +
            "</div>" +
            "<p style='font-size:13px; color:#777;'>Válido por 30 minutos.</p>" +
            "<p style='font-size:12px; color:#aaa;'>Se você não solicitou a redefinição, ignore este e-mail. " +
            "Sua senha permanece a mesma.</p>";

        send(toEmail, null, "Fala, Cidade! – Código de Recuperação de Senha", withFooter(text), layout(content),
             "Falha ao enviar e-mail de recuperação de senha");
    }

    /** Código de verificação do login em duas etapas (válido por 10 min). */
    @Override
    @Async("emailExecutor")
    public void sendMfaCodeEmail(String toEmail, String code) {
        String text =
            "Seu código de verificação é: " + code + "\n\n" +
            "Ele é válido por 10 minutos. Se você não tentou fazer login, ignore este e-mail.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Verificação em duas etapas</h2>" +
            "<p style='font-size:15px; color:#333;'>Use o código abaixo para concluir seu login:</p>" +
            "<div style='font-size:32px; font-weight:bold; letter-spacing:8px; color:" + BRAND_COLOR + ";" +
            "            background:#eef4fb; border-radius:8px; padding:16px; margin:20px 0; text-align:center;'>" +
                 code +
            "</div>" +
            "<p style='font-size:13px; color:#777;'>Válido por 10 minutos. " +
            "Se você não tentou fazer login, ignore este e-mail.</p>";

        send(toEmail, null, "Fala, Cidade! – Código de verificação", withFooter(text), layout(content),
             "Falha ao enviar código de verificação por e-mail");
    }

    /** Código para confirmar a desativação do MFA (com alerta de segurança). */
    @Override
    @Async("emailExecutor")
    public void sendMfaDeactivationEmail(String toEmail, String code) {
        String text =
            "Seu código para desativar a verificação em duas etapas é: " + code + "\n\n" +
            "Ele é válido por 10 minutos. ATENÇÃO: Se você não solicitou esta desativação, " +
            "sua conta pode estar em risco. Altere sua senha imediatamente.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Desativação de segurança</h2>" +
            "<p style='font-size:15px; color:#333;'>Use o código abaixo para <strong>desativar</strong> " +
            "a verificação em duas etapas da sua conta:</p>" +
            "<div style='font-size:32px; font-weight:bold; letter-spacing:8px; color:#D9534F;" +
            "            background:#fdf0ef; border-radius:8px; padding:16px; margin:20px 0; text-align:center;'>" +
                 code +
            "</div>" +
            "<p style='font-size:13px; color:#777;'>Válido por 10 minutos.</p>" +
            "<p style='font-size:13px; color:#D9534F; font-weight:bold; border-top:1px solid #eee; padding-top:14px;'>" +
            "Atenção: se você não solicitou esta ação, sua conta pode estar comprometida. Altere sua senha imediatamente.</p>";

        send(toEmail, null, "Fala, Cidade! – Desativação de segurança (MFA)", withFooter(text), layout(content),
             "Falha ao enviar código de desativação do MFA por e-mail");
    }

    /** Notifica a equipe sobre mensagem do formulário de contato (Reply-To = cidadão). */
    @Override
    @Async("emailExecutor")
    public void sendContactNotification(String toEmail, String senderName, String senderEmail,
                                        String subject, String message) {
        String safeSubject = (subject == null || subject.isBlank()) ? "Sem assunto" : subject;

        String text =
            "Nova mensagem pelo formulário de contato.\n\n" +
            "Nome: " + senderName + "\n" +
            "E-mail: " + senderEmail + "\n" +
            "Assunto: " + safeSubject + "\n\n" +
            "Mensagem:\n" + message;

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Nova mensagem de contato</h2>" +
            "<p style='font-size:14px; color:#333; margin:4px 0;'><strong>Nome:</strong> " + senderName + "</p>" +
            "<p style='font-size:14px; color:#333; margin:4px 0;'><strong>E-mail:</strong> " + senderEmail + "</p>" +
            "<p style='font-size:14px; color:#333; margin:4px 0;'><strong>Assunto:</strong> " + safeSubject + "</p>" +
            "<hr style='border:none; border-top:1px solid #eee; margin:18px 0;'>" +
            "<p style='font-size:14px; color:#333; white-space:pre-wrap;'>" + message + "</p>";

        send(toEmail, senderEmail, "Fala, Cidade! – Contato: " + safeSubject, withFooter(text), layout(content),
             "Falha ao enviar notificação de contato por e-mail");
    }

    /** E-mail de boas-vindas após o cadastro. */
    @Override
    @Async("emailExecutor")
    public void sendWelcomeEmail(String toEmail, String fullname) {
        String text =
            "Olá, " + fullname + "!\n\n" +
            "Seu cadastro foi realizado com sucesso.\n" +
            "Agora você pode registrar ocorrências e acompanhar o andamento das suas solicitações.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Bem-vindo(a)! 🎉</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + fullname + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Seu cadastro foi realizado com sucesso. " +
            "Agora você faz parte da comunidade que está transformando Santa Rita do Sapucaí.</p>" +
            "<p style='font-size:15px; color:#333;'>Com o Fala, Cidade! você pode:</p>" +
            "<ul style='font-size:14px; color:#444; line-height:1.8;'>" +
            "  <li>📍 Registrar ocorrências na cidade com foto e localização</li>" +
            "  <li>🔔 Acompanhar o status das suas ocorrências</li>" +
            "  <li>🤝 Apoiar ocorrências de outros cidadãos</li>" +
            "</ul>";

        send(toEmail, null, "Bem-vindo(a) ao Fala, Cidade!", withFooter(text), layout(content),
             "Falha ao enviar e-mail de boas-vindas");
    }

    /** Conta de Funcionário/Administrador criada por um administrador. */
    @Override
    @Async("emailExecutor")
    public void sendStaffWelcomeEmail(String toEmail, String fullname, UserModel.UserRole role, String city) {
        String name = (fullname == null || fullname.isBlank()) ? "colega" : fullname;
        boolean isAdmin = role == UserModel.UserRole.ADMINISTRATOR
                       || role == UserModel.UserRole.SUPER_ADMIN;
        String roleLabel = isAdmin ? "Administrador" : "Funcionário";
        String cityLabel = (city == null || city.isBlank()) ? "não informado" : city;

        String permissions = isAdmin
            ? "Painel de gestão com os indicadores de demanda\n" +
              "Todas as ocorrências do sistema\n" +
              "Cadastro de funcionários e administradores\n" +
              "Relatórios de desempenho por categoria"
            : "Ocorrências registradas no seu município\n" +
              "Atualização do status das ocorrências\n" +
              "Histórico e dados dos autores das ocorrências";

        String permissionsHtml = isAdmin
            ? "  <li>📊 Painel de gestão com os indicadores de demanda</li>" +
              "  <li>🗂️ Todas as ocorrências do sistema</li>" +
              "  <li>👥 Cadastro de funcionários e administradores</li>" +
              "  <li>📈 Relatórios de desempenho por categoria</li>"
            : "  <li>🗂️ Ocorrências registradas no seu município</li>" +
              "  <li>🔄 Atualização do status das ocorrências</li>" +
              "  <li>🔍 Histórico e dados dos autores das ocorrências</li>";

        String text =
            "Olá, " + name + "!\n\n" +
            "Um administrador criou uma conta de " + roleLabel + " para você no Fala, Cidade!.\n\n" +
            "Município vinculado: " + cityLabel + "\n" +
            "E-mail de acesso: " + toEmail + "\n\n" +
            "Com esta conta você tem acesso a:\n" + permissions + "\n\n" +
            "Importante: a senha inicial foi definida pelo administrador. " +
            "Troque-a no primeiro acesso.\n" +
            "No primeiro login o sistema pede a configuração obrigatória da " +
            "verificação em duas etapas (2FA).";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Sua conta de " + roleLabel + " foi criada</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + name + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Um administrador criou uma conta de " +
            "<strong>" + roleLabel + "</strong> para você no Fala, Cidade!.</p>" +

            "<div style='background:#eef4fb; border-radius:8px; padding:14px 16px; margin:20px 0;'>" +
            "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>Município vinculado</p>" +
            "  <p style='font-size:17px; font-weight:bold; color:" + BRAND_COLOR + "; margin:0 0 12px;'>" +
                 cityLabel + "</p>" +
            "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>E-mail de acesso</p>" +
            "  <p style='font-size:15px; color:#333; margin:0;'>" + toEmail + "</p>" +
            "</div>" +

            (isAdmin ? "" :
            "<p style='font-size:13px; color:#777; margin:-8px 0 18px;'>" +
            "Você enxerga as ocorrências deste município. Se ele estiver incorreto, " +
            "peça a um administrador para corrigir o cadastro.</p>") +

            "<p style='font-size:15px; color:#333;'>Com esta conta você tem acesso a:</p>" +
            "<ul style='font-size:14px; color:#444; line-height:1.8;'>" + permissionsHtml + "</ul>" +

            "<div style='border-top:1px solid #eee; margin-top:22px; padding-top:16px;'>" +
            "  <p style='font-size:14px; color:#333; margin:0 0 8px;'><strong>Antes do primeiro acesso</strong></p>" +
            "  <p style='font-size:14px; color:#555; margin:0 0 6px;'>" +
            "    A senha inicial foi definida pelo administrador — troque-a no primeiro acesso.</p>" +
            "  <p style='font-size:14px; color:#555; margin:0;'>" +
            "    O sistema pedirá a configuração obrigatória da verificação em duas etapas (2FA).</p>" +
            "</div>";

        send(toEmail, null, "Fala, Cidade! – Sua conta de " + roleLabel + " foi criada",
             withFooter(text), layout(content), "Falha ao enviar e-mail de boas-vindas da equipe");
    }

    /**
     * RF15/RF25: a conta foi alterada por outra pessoa. O corpo diz o que mudou,
     * campo a campo, e por quem — sem isso a pessoa só descobre a mudança pela
     * consequência (uma ocorrência que sumiu da lista, um acesso que deixou de
     * funcionar) e não tem a quem recorrer.
     */
    @Override
    @Async("emailExecutor")
    public void sendAccountChangedEmail(String toEmail, String fullname, java.util.List<String> changes,
                                        String changedByName, String changedByEmail) {
        if (changes == null || changes.isEmpty()) return;
        String name  = (fullname == null || fullname.isBlank()) ? "colega" : fullname;
        String autor = (changedByName == null || changedByName.isBlank())
                     ? "um administrador" : changedByName;
        String autorContato = (changedByEmail == null || changedByEmail.isBlank())
                            ? "" : " (" + changedByEmail + ")";

        StringBuilder lista     = new StringBuilder();
        StringBuilder listaHtml = new StringBuilder();
        for (String change : changes) {
            lista.append("- ").append(change).append("\n");
            listaHtml.append("  <li style='margin-bottom:6px;'>").append(esc(change)).append("</li>");
        }

        String text =
            "Olá, " + name + "!\n\n" +
            "Os dados da sua conta no Fala, Cidade! foram alterados por " + autor + autorContato + ".\n\n" +
            "O que mudou:\n" + lista + "\n" +
            "Se você não reconhece esta alteração, responda a este e-mail ou procure a " +
            "administração do seu município.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Sua conta foi alterada</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + esc(name) + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Os dados da sua conta foram alterados por " +
            "<strong>" + esc(autor) + "</strong>" + esc(autorContato) + ".</p>" +

            "<div style='background:#eef4fb; border-radius:8px; padding:14px 16px; margin:20px 0;'>" +
            "  <p style='font-size:13px; color:#777; margin:0 0 8px;'>O que mudou</p>" +
            "  <ul style='font-size:14px; color:#333; line-height:1.6; margin:0; padding-left:18px;'>" +
                 listaHtml + "</ul>" +
            "</div>" +

            "<p style='font-size:13px; color:#777; margin:0;'>" +
            "Se você não reconhece esta alteração, responda a este e-mail ou procure a " +
            "administração do seu município.</p>";

        send(toEmail, changedByEmail, "Fala, Cidade! – Sua conta foi alterada",
             withFooter(text), layout(content), "Falha ao enviar aviso de alteração de conta");
    }

    /** Confirma o registro da ocorrência e agradece o comprometimento com a cidade. */
    @Override
    @Async("emailExecutor")
    public void sendOccurrenceCreatedEmail(String toEmail, String fullname, String protocol, String title) {
        String name = (fullname == null || fullname.isBlank()) ? "cidadão" : fullname;
        String safeTitle = (title == null || title.isBlank()) ? "" : " – " + title;

        String text =
            "Olá, " + name + "!\n\n" +
            "Sua ocorrência" + safeTitle + " foi registrada com sucesso.\n" +
            "Protocolo: " + protocol + "\n\n" +
            "Você pode acompanhar o andamento pelo aplicativo a qualquer momento.\n\n" +
            "Obrigado pelo seu comprometimento com a cidade! Registros como o seu " +
            "ajudam a administração pública a agir mais rápido.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Ocorrência registrada com sucesso ✅</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + name + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Sua ocorrência" + safeTitle + " foi registrada. " +
            "Guarde o protocolo para acompanhar o andamento:</p>" +
            "<div style='font-family:monospace; font-size:22px; font-weight:bold; color:" + BRAND_COLOR + ";" +
            "            background:#eef4fb; border-radius:8px; padding:14px; margin:20px 0; text-align:center;'>" +
                 protocol +
            "</div>" +
            "<p style='font-size:15px; color:#333;'><strong>Obrigado pelo seu comprometimento com a cidade!</strong> " +
            "Registros como o seu ajudam a administração pública a agir mais rápido.</p>";

        send(toEmail, null, "Fala, Cidade! – Ocorrência registrada (" + protocol + ")",
             withFooter(text), layout(content), "Falha ao enviar e-mail de ocorrência registrada");
    }

    /** Notifica o autor sobre a mudança de status (mensagem do funcionário é opcional). */
    @Override
    @Async("emailExecutor")
    public void sendStatusChangeEmail(String toEmail, String fullname, String protocol,
                                      String newStatus, String message) {
        String name = (fullname == null || fullname.isBlank()) ? "cidadão" : fullname;
        String statusLabel = switch (newStatus) {
            case "EM_ANDAMENTO" -> "Em Andamento";
            case "CONCLUIDA"    -> "Concluída";
            case "INDEFERIDA"   -> "Indeferida";
            default             -> newStatus;
        };
        String statusColor = switch (newStatus) {
            case "EM_ANDAMENTO" -> "#d97706";
            case "CONCLUIDA"    -> "#16a34a";
            case "INDEFERIDA"   -> "#6b7280";
            default             -> BRAND_COLOR;
        };
        boolean hasMessage = message != null && !message.isBlank();

        String text =
            "Olá, " + name + "!\n\n" +
            "O status da sua ocorrência (protocolo " + protocol + ") foi atualizado para: " + statusLabel + ".\n" +
            (hasMessage ? "\nMensagem da equipe:\n" + message + "\n" : "") +
            "\nVocê pode ver os detalhes pelo aplicativo a qualquer momento.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Sua ocorrência foi atualizada</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + name + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>O status da ocorrência de protocolo " +
            "<strong style='font-family:monospace;'>" + protocol + "</strong> mudou para:</p>" +
            "<div style='text-align:center; margin:20px 0;'>" +
            "  <span style='display:inline-block; background:" + statusColor + "22; color:" + statusColor + ";" +
            "        padding:10px 24px; border-radius:999px; font-size:16px; font-weight:bold;'>" + statusLabel + "</span>" +
            "</div>" +
            (hasMessage
                ? "<div style='background:#f9fafb; border-left:4px solid " + BRAND_COLOR + "; border-radius:6px;" +
                  "     padding:14px 16px; margin:18px 0;'>" +
                  "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>Mensagem da equipe:</p>" +
                  "  <p style='font-size:15px; color:#333; margin:0; white-space:pre-wrap;'>" + message + "</p>" +
                  "</div>"
                : "") +
            "<p style='font-size:14px; color:#555;'>Você pode ver os detalhes pelo aplicativo a qualquer momento.</p>";

        send(toEmail, null, "Fala, Cidade! – Ocorrência " + protocol + ": " + statusLabel,
             withFooter(text), layout(content), "Falha ao enviar e-mail de mudança de status");
    }


    /**
     * RF22: encaminhamento ao departamento responsável.
     *
     * O corpo carrega apenas o que descreve o problema — protocolo, categoria,
     * prioridade, endereço, data e relato. Nome, e-mail e endereço de rede do
     * autor ficam de fora, inclusive nas ocorrências identificadas: o
     * departamento precisa do problema, não de quem o relatou (LGPD).
     *
     * Envio síncrono, ao contrário dos demais e-mails do sistema: quem
     * encaminha precisa saber se a mensagem saiu antes de a ocorrência mudar
     * de estado.
     */
    @Override
    public void sendOccurrenceForwardEmail(String toEmail, String departmentName,
                                           GetOccurrenceDto o) {
        String protocol = o.getProtocolNumber();
        String category = label(o.getType() == null ? null : o.getType().name());
        String priority = o.getPriority() == null ? "—" : label(o.getPriority().name());
        String address  = address(o);
        String opened   = o.getCreatedAt() == null ? "—"
            : o.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String maps = o.getLatitude() != null && o.getLongitude() != null
            ? "https://www.openstreetmap.org/?mlat=" + o.getLatitude() + "&mlon=" + o.getLongitude() + "#map=18/"
              + o.getLatitude() + "/" + o.getLongitude()
            : null;

        List<Photo> photos = downloadPhotos(o);

        String text =
            "Encaminhamento de ocorrência — " + departmentName + "\n\n" +
            "Protocolo: " + protocol + "\n" +
            "Categoria: " + category + "\n" +
            "Prioridade: " + priority + "\n" +
            "Registrada em: " + opened + "\n" +
            "Endereço: " + address + "\n" +
            (maps != null ? "Local no mapa: " + maps + "\n" : "") +
            "\nRelato:\n" + nvl(o.getDescription()) + "\n\n" +
            (photos.isEmpty()
                ? "Sem fotografias anexadas.\n"
                : photos.size() + " fotografia(s) em anexo.\n") +
            "\nEste encaminhamento não contém dados pessoais do autor da ocorrência.";

        String content =
            "<h2 style='margin:0 0 4px; font-size:20px; color:#111;'>Ocorrência encaminhada</h2>" +
            "<p style='font-size:15px; color:#333; margin:0 0 20px;'>" +
            "  Esta ocorrência foi encaminhada ao <strong>" + esc(departmentName) + "</strong> " +
            "  para providências." +
            "</p>" +
            "<div style='background:#eef4fb; border-radius:8px; padding:14px 16px; margin:0 0 20px;'>" +
            "  <p style='font-size:12px; color:#777; margin:0 0 4px;'>Protocolo</p>" +
            "  <p style='font-size:20px; font-weight:bold; letter-spacing:1px; color:" + BRAND_COLOR + "; margin:0;'>" +
                 esc(protocol) + "</p>" +
            "</div>" +
            row("Categoria", category) +
            row("Prioridade", priority) +
            row("Registrada em", opened) +
            row("Endereço", address) +
            (maps != null
                ? "<p style='font-size:14px; margin:14px 0 0;'>" +
                  "<a href='" + maps + "' style='color:" + BRAND_COLOR + ";'>Ver o local no mapa</a></p>"
                : "") +
            "<div style='margin:20px 0 0; padding:14px 16px; background:#f9fafb;" +
            "     border-left:4px solid " + BRAND_COLOR + "; border-radius:6px;'>" +
            "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>Relato do cidadão</p>" +
            "  <p style='font-size:15px; color:#333; margin:0; white-space:pre-wrap;'>" +
                 esc(nvl(o.getDescription())) + "</p>" +
            "</div>" +
            (photos.isEmpty()
                ? "<p style='font-size:13px; color:#777; margin:18px 0 0;'>Sem fotografias anexadas.</p>"
                : "<p style='font-size:13px; color:#777; margin:18px 0 0;'>" + photos.size() +
                  " fotografia(s) em anexo, com rostos e placas desfocados.</p>") +
            "<p style='font-size:12px; color:#aaa; margin:14px 0 0;'>" +
            "  Este encaminhamento não contém dados pessoais do autor da ocorrência (LGPD).</p>";

        sendWithAttachments(toEmail, "Fala, Cidade! – Ocorrência " + protocol + " encaminhada",
            withFooter(text), layout(content), photos,
            "Falha ao encaminhar a ocorrência ao departamento");
    }

    /** Linha rótulo/valor do corpo do e-mail de encaminhamento. */
    private String row(String label, String value) {
        return "<p style='font-size:14px; color:#333; margin:0 0 6px;'>" +
               "<span style='color:#777;'>" + label + ":</span> " + esc(value) + "</p>";
    }

    private String address(GetOccurrenceDto o) {
        StringBuilder sb = new StringBuilder();
        if (o.getStreet() != null && !o.getStreet().isBlank()) sb.append(o.getStreet());
        if (o.getNumber() != null && !o.getNumber().isBlank()) sb.append(", ").append(o.getNumber());
        if (o.getNeighborhood() != null && !o.getNeighborhood().isBlank()) sb.append(" — ").append(o.getNeighborhood());
        if (o.getCity() != null && !o.getCity().isBlank()) sb.append(", ").append(o.getCity());
        if (o.getAddressReference() != null && !o.getAddressReference().isBlank())
            sb.append(" (referência: ").append(o.getAddressReference()).append(")");
        return sb.length() == 0 ? "—" : sb.toString();
    }

    /**
     * Rótulos das enumerações como o cidadão e o servidor os leem na tela.
     * O e-mail vai para fora do sistema, então não pode mostrar
     * SINALIZACAO_OU_SEMAFORO_COM_DEFEITO nem "Media" sem acento.
     */
    private static final java.util.Map<String, String> LABELS = java.util.Map.ofEntries(
        java.util.Map.entry("BURACO_NA_RUA_OU_CALCADA",              "Buraco na rua ou calçada"),
        java.util.Map.entry("POSTE_COM_LUZ_QUEIMADA",                "Poste com luz queimada"),
        java.util.Map.entry("LIXO_ACUMULADO_OU_TERRENO_SUJO",        "Lixo acumulado ou terreno sujo"),
        java.util.Map.entry("SINALIZACAO_OU_SEMAFORO_COM_DEFEITO",   "Sinalização ou semáforo com defeito"),
        java.util.Map.entry("PROBLEMAS_EM_PRACAS_E_PARQUES",         "Problemas em praças e parques"),
        java.util.Map.entry("FALHAS_NO_TRANSPORTE_PUBLICO",          "Falhas no transporte público"),
        java.util.Map.entry("PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA", "Problemas em posto de saúde ou escola"),
        java.util.Map.entry("SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO",    "Som alto ou perturbação do sossego"),
        java.util.Map.entry("OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO",   "Obra irregular ou imóvel abandonado"),
        java.util.Map.entry("MAUS_TRATOS_AOS_ANIMAIS",               "Maus-tratos aos animais"),
        java.util.Map.entry("PESSOA_PRECISANDO_DE_AJUDA",            "Pessoa precisando de ajuda"),
        java.util.Map.entry("OUTROS_PROBLEMAS",                      "Outros problemas"),
        java.util.Map.entry("PENDENTE",     "Pendente"),
        java.util.Map.entry("EM_ANDAMENTO", "Em andamento"),
        java.util.Map.entry("CONCLUIDA",    "Concluída"),
        java.util.Map.entry("INDEFERIDA",   "Indeferida"),
        java.util.Map.entry("ALTA",  "Alta"),
        java.util.Map.entry("MEDIA", "Média"),
        java.util.Map.entry("BAIXA", "Baixa")
    );

    /** Rótulo da enumeração; sem correspondência, troca o sublinhado por espaço. */
    private String label(String enumName) {
        if (enumName == null || enumName.isBlank()) return "—";
        String known = LABELS.get(enumName);
        if (known != null) return known;
        String s = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String nvl(String s) { return s == null || s.isBlank() ? "—" : s; }

    /** Escapa o que vem do cidadão antes de entrar no HTML do e-mail. */
    private String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Fotografia baixada do serviço de mídias para seguir anexada. */
    private record Photo(String filename, byte[] bytes, String contentType) {}

    /**
     * Baixa as fotografias da ocorrência. As URLs já são as de entrega, com o
     * desfoque de rostos e placas aplicado. Falha no download não impede o
     * encaminhamento: o e-mail segue sem o anexo correspondente.
     */
    private List<Photo> downloadPhotos(GetOccurrenceDto o) {
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        if (o.getUrlMedia() != null && !o.getUrlMedia().isBlank()) urls.add(o.getUrlMedia());
        if (o.getMedia() != null)
            for (var m : o.getMedia())
                if (m.getUrl() != null && !m.getUrl().isBlank()) urls.add(m.getUrl());

        List<Photo> photos = new java.util.ArrayList<>();
        int i = 1;
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5)).build();
        for (String url : urls) {
            try {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15)).GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || response.body().length == 0) continue;
                String type = response.headers().firstValue("content-type").orElse("image/jpeg");
                String ext  = type.contains("png") ? "png" : type.contains("webp") ? "webp" : "jpg";
                photos.add(new Photo("ocorrencia-" + o.getProtocolNumber() + "-" + i + "." + ext,
                                     response.body(), type));
                i++;
            } catch (Exception ignored) { /* anexo é melhor esforço */ }
        }
        return photos;
    }

    /** Mesmo envio multipart dos outros e-mails, com as fotografias anexadas. */
    private void sendWithAttachments(String to, String subject, String text, String html,
                                     List<Photo> photos, String failureMessage) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            for (Photo p : photos)
                helper.addAttachment(p.filename(),
                    new org.springframework.core.io.ByteArrayResource(p.bytes()), p.contentType());
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new RuntimeException(failureMessage, e);
        }
    }

    // ── Layout padrão ───────────────────────────────────────────────────────


    /** Envolve o conteúdo no layout padrão: marca no topo, cartão branco e rodapé escuro. */
    private String layout(String contentHtml) {
        return
            "<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'></head>" +
            "<body style='margin:0; padding:0; background:#f4f4f4; font-family:Arial, sans-serif;'>" +
            "<div style='max-width:560px; margin:0 auto; padding:24px 16px;'>" +

            // Marca do sistema no topo
            "  <div style='padding:8px 4px 16px;'>" +
            "    <span style='font-size:24px; font-weight:800; color:" + BRAND_COLOR + ";'>Fala, Cidade!</span>" +
            "  </div>" +

            // Cartão branco com o conteúdo
            "  <div style='background:#fff; border-radius:10px; padding:32px 28px;" +
            "       box-shadow:0 2px 8px rgba(0,0,0,0.08);'>" +
                 contentHtml +
            "  </div>" +

            // Rodapé escuro com contatos e endereço
            "  <div style='background:#1a1a1a; border-radius:10px; padding:24px 28px; margin-top:16px;'>" +
            "    <p style='color:#fff; font-size:15px; font-weight:bold; margin:0 0 12px;'>" +
            "      Abraços,<br>Equipe Fala, Cidade!</p>" +
            "    <p style='color:#9ca3af; font-size:12px; margin:0 0 4px;'>" +
            "      Dúvidas? Fale com a gente: " +
            "      <a href='mailto:" + contactEmail + "' style='color:#9ca3af;'>" + contactEmail + "</a></p>" +
            "    <p style='color:#9ca3af; font-size:12px; margin:0 0 12px;'>" + ADDRESS + "</p>" +
            "    <p style='color:#6b7280; font-size:11px; margin:0;'>" +
            "      Por favor, não responda este e-mail — trata-se de uma mensagem automática.</p>" +
            "  </div>" +

            "</div></body></html>";
    }

    /** Rodapé da versão texto puro (fallback). */
    private String withFooter(String text) {
        return text + "\n\n—\nEquipe Fala, Cidade!\n" +
               "Contato: " + contactEmail + "\n" + ADDRESS + "\n" +
               "Mensagem automática — não responda este e-mail.";
    }

    /**
     * Monta e envia um e-mail multipart UTF-8 (texto puro como fallback + HTML).
     * replyTo é opcional; failureMessage vira a mensagem da exceção em caso de erro.
     */
    private void send(String to, String replyTo, String subject,
                      String text, String html, String failureMessage) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new RuntimeException(failureMessage, e);
        }
    }
}
