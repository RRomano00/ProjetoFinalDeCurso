-- ============================================================
-- FALA CIDADE – Script de População de Tabelas (Modo JWT)
-- Cidade: Santa Rita do Sapucaí – MG
--
-- Senhas encriptadas com crypt(senha, gen_salt('bf', 12))
-- do pgcrypto — compatível com BCryptPasswordEncoder do Spring.
-- O Spring valida com passwordEncoder.matches(plainText, hash).
--
-- Credenciais de acesso:
--   admin@falacidade.com   → Admin@1234
--   carlos@prefeitura.com  → Func@2024
--   ana@prefeitura.com     → Func@2024
--   joao.silva@email.com   → Citizen@1
--   maria.souza@email.com  → Citizen@1
--   pedro.santos@email.com → Citizen@1
--   lucia.lima@email.com   → Citizen@1
--
-- Rastreamento anônimo (demo):
--   GET /api/occurrence/anonymous-status?trackingCode=B7KN4PX2
--   GET /api/occurrence/anonymous-status?trackingCode=M3RP6TW9
-- ============================================================

-- ============================================================
-- 1. USUÁRIOS
-- ============================================================
INSERT INTO users (fullname, email, password, date_of_birth, phone_number,
                   street, neighborhood, number, cep, city,
                   role, is_active, accepts_terms)
VALUES

('Administrador do Sistema',
 'admin@falacidade.com',
 crypt('Admin@1234', gen_salt('bf', 12)),
 NULL, '35988001100',
 NULL, NULL, NULL, NULL, NULL,
 'ADMINISTRATOR', TRUE, TRUE),

('Carlos Eduardo Martins',
 'carlos@prefeitura.com',
 crypt('Func@2024', gen_salt('bf', 12)),
 '1985-03-14', '35988002200',
 'Rua Dr. João Pessoa', 'Centro', '210', '37540-000', 'Santa Rita do Sapucaí',
 'EMPLOYEE', TRUE, TRUE),

('Ana Beatriz Ferreira',
 'ana@prefeitura.com',
 crypt('Func@2024', gen_salt('bf', 12)),
 '1990-07-22', '35988003300',
 'Av. Prefeito Olavo Gomes de Oliveira', 'Centro', '450', '37540-000', 'Santa Rita do Sapucaí',
 'EMPLOYEE', TRUE, TRUE),

('João Carlos Silva',
 'joao.silva@email.com',
 crypt('Citizen@1', gen_salt('bf', 12)),
 '1988-11-05', '35991234567',
 'Rua Caetano Moreira da Costa', 'Centro', '78', '37540-000', 'Santa Rita do Sapucaí',
 'CITIZEN', TRUE, TRUE),

('Maria Aparecida Souza',
 'maria.souza@email.com',
 crypt('Citizen@1', gen_salt('bf', 12)),
 '1995-02-18', '35992345678',
 'Av. Francisco de Paula Quintanilha Ribeiro', 'Bairro Fátima', '132', '37540-000', 'Santa Rita do Sapucaí',
 'CITIZEN', TRUE, TRUE),

('Pedro Henrique Santos',
 'pedro.santos@email.com',
 crypt('Citizen@1', gen_salt('bf', 12)),
 '1975-08-30', '35993456789',
 'Rua Dr. João Pessoa', 'Centro', '550', '37540-000', 'Santa Rita do Sapucaí',
 'CITIZEN', TRUE, TRUE),

('Lúcia Helena Lima',
 'lucia.lima@email.com',
 crypt('Citizen@1', gen_salt('bf', 12)),
 '2000-05-12', '35994567890',
 'Rua Sete de Setembro', 'Bairro Sinhazinha', '22', '37540-000', 'Santa Rita do Sapucaí',
 'CITIZEN', TRUE, TRUE)

ON CONFLICT (email) DO NOTHING;


-- ============================================================
-- 2. OCORRÊNCIAS
-- Imagens: URLs públicas do Cloudinary demo account
-- Coordenadas reais de Santa Rita do Sapucaí – MG
-- ============================================================
INSERT INTO occurrence (
  protocol_number, title, description,
  street, number, neighborhood, city,
  latitude, longitude,
  url_media, cloudinary_public_id, image_blurred,
  type, status, priority,
  is_anonymous, anonymous_tracking_code_hash,
  users_id, created_at, updated_at
)
VALUES

-- 1. ATENDIDA – buraco na rua com foto nítida
('FC-20260510-A1B2C',
 'Buraco perigoso na Rua Caetano Moreira da Costa',
 'Há um buraco de aproximadamente 50 cm de diâmetro e 30 cm de profundidade na Rua Caetano Moreira da Costa, próximo ao número 78. Já causou queda de motocicleta na semana passada. Situação de risco para pedestres e veículos, especialmente à noite pois não há sinalização.',
 'Rua Caetano Moreira da Costa', '78', 'Centro', 'Santa Rita do Sapucaí',
 -22.8647, -45.7032,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/landscapes/nature-mountains.jpg',
 'samples/landscapes/nature-mountains', FALSE,
 'BURACO_NA_RUA_OU_CALCADA', 'ATENDIDA', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'joao.silva@email.com'),
 NOW() - INTERVAL '25 days', NOW() - INTERVAL '5 days'),

-- 2. EM_ANDAMENTO – poste sem luz com foto nítida
('FC-20260515-D3E4F',
 'Poste apagado há duas semanas – Av. Francisco de Paula Quintanilha Ribeiro',
 'O poste localizado na Av. Francisco de Paula Quintanilha Ribeiro esquina com a Rua Caetano Moreira da Costa está com a lâmpada queimada há pelo menos 14 dias. À noite o trecho fica completamente escuro, gerando insegurança para os moradores.',
 'Av. Francisco de Paula Quintanilha Ribeiro', NULL, 'Bairro Fátima', 'Santa Rita do Sapucaí',
 -22.8659, -45.7018,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/people/smiling-man.jpg',
 'samples/people/smiling-man', FALSE,
 'POSTE_COM_LUZ_QUEIMADA', 'EM_ANDAMENTO', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'maria.souza@email.com'),
 NOW() - INTERVAL '20 days', NOW() - INTERVAL '3 days'),

-- 3. PENDENTE – lixo acumulado com foto nítida
('FC-20260518-G5H6I',
 'Acúmulo de lixo em terreno abandonado – Rua Dr. João Pessoa',
 'Terreno baldio na Rua Dr. João Pessoa, próximo ao número 550, acumula lixo doméstico há mais de um mês. O mau cheiro já afeta os estabelecimentos comerciais vizinhos. Há presença de ratos e outros animais no local.',
 'Rua Dr. João Pessoa', '550', 'Centro', 'Santa Rita do Sapucaí',
 -22.8635, -45.7025,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/food/dessert.jpg',
 'samples/food/dessert', FALSE,
 'LIXO_ACUMULADO_OU_TERRENO_SUJO', 'PENDENTE', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'pedro.santos@email.com'),
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),

-- 4. PENDENTE – semáforo com defeito – ALTA – sem foto
('FC-20260520-J7K8L',
 'Semáforo piscando em amarelo – Av. Prefeito Olavo Gomes de Oliveira',
 'O semáforo do cruzamento da Av. Prefeito Olavo Gomes de Oliveira com a Rua Dr. João Pessoa está funcionando apenas no modo piscante amarelo desde domingo. Trata-se de um cruzamento de alto fluxo com dois quase-acidentes hoje.',
 'Av. Prefeito Olavo Gomes de Oliveira', NULL, 'Centro', 'Santa Rita do Sapucaí',
 -22.8622, -45.7041,
 NULL, NULL, FALSE,
 'SINALIZACAO_OU_SEMAFORO_COM_DEFEITO', 'PENDENTE', 'ALTA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'lucia.lima@email.com'),
 NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),

-- 5. PENDENTE – praça – BAIXA – sem foto
('FC-20260522-M9N0O',
 'Brinquedos quebrados na Praça da Matriz',
 'Os brinquedos instalados na Praça da Matriz estão em condições precárias: balanço sem assento, escorregador com fresta de metal exposta e gira-gira travado. Risco para crianças.',
 'Praça da Matriz', NULL, 'Centro', 'Santa Rita do Sapucaí',
 -22.8641, -45.7038,
 NULL, NULL, FALSE,
 'PROBLEMAS_EM_PRACAS_E_PARQUES', 'PENDENTE', 'BAIXA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'joao.silva@email.com'),
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),

-- 6. PENDENTE – maus tratos – ALTA – foto com flag de BORRADA (image_blurred=TRUE)
('FC-20260525-P1Q2R',
 'Denúncia de maus tratos a cão – Rua Sete de Setembro',
 'Vizinho mantém um cão de grande porte acorrentado em espaço mínimo, sem abrigo, ração ou água. Animal apresenta costelas visíveis indicando desnutrição grave. Caso precisa de atendimento urgente da vigilância animal.',
 'Rua Sete de Setembro', '22', 'Bairro Sinhazinha', 'Santa Rita do Sapucaí',
 -22.8668, -45.7055,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/animals/cat.jpg',
 'samples/animals/cat', TRUE,
 'MAUS_TRATOS_AOS_ANIMAIS', 'PENDENTE', 'ALTA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'lucia.lima@email.com'),
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),

-- 7. EM_ANDAMENTO – ANÔNIMA – pessoa em risco – ALTA – sem foto
-- Código de rastreamento: B7KN4PX2 | SHA-256: 7e96f243ce080c309643afa5adfd36bcb83eecd3ded45ed1319e39347b9f00ac
('FC-20260526-S3T4U',
 'Pessoa em estado grave próximo à rodoviária',
 'Uma pessoa está deitada na calçada da Av. Prefeito Olavo Gomes de Oliveira próximo à rodoviária, aparentemente inconsciente. Apresenta tremores e não responde a estímulos.',
 'Av. Prefeito Olavo Gomes de Oliveira', NULL, 'Centro', 'Santa Rita do Sapucaí',
 -22.8615, -45.7048,
 NULL, NULL, FALSE,
 'PESSOA_PRECISANDO_DE_AJUDA', 'EM_ANDAMENTO', 'ALTA',
 TRUE, '7e96f243ce080c309643afa5adfd36bcb83eecd3ded45ed1319e39347b9f00ac',
 NULL,
 NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),

-- 8. ATENDIDA – obra irregular com foto nítida
('FC-20260527-V5W6X',
 'Obra sem tapume bloqueando calçada – Rua Dr. João Pessoa',
 'Construção na Rua Dr. João Pessoa sem tapume de proteção e com entulho na calçada, obrigando pedestres a caminhar na rua. Obra sem alvará visível.',
 'Rua Dr. João Pessoa', '120', 'Centro', 'Santa Rita do Sapucaí',
 -22.8631, -45.7029,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/imagecon-group.jpg',
 'samples/imagecon-group', FALSE,
 'OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO', 'ATENDIDA', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'maria.souza@email.com'),
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '10 days'),

-- 9. PENDENTE – transporte público – sem foto
('FC-20260530-Y7Z8A',
 'Ônibus linha 03 não cumpre horário há semanas',
 'O ônibus da linha 03 está atrasando entre 40 e 60 minutos nos horários de pico. Trabalhadores chegam atrasados e crianças perdem aula. Situação se repete há três semanas.',
 'Terminal Urbano', NULL, 'Centro', 'Santa Rita do Sapucaí',
 -22.8628, -45.7021,
 NULL, NULL, FALSE,
 'FALHAS_NO_TRANSPORTE_PUBLICO', 'PENDENTE', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM users WHERE email = 'pedro.santos@email.com'),
 NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),

-- 10. PENDENTE – ANÔNIMA – barulho – BAIXA – sem foto
-- Código de rastreamento: M3RP6TW9 | SHA-256: 749df37cbbf9192d35d01fb047b3415b516849a4e970be0fad2de244e13563fb
('FC-20260601-B9C0D',
 'Som alto todos os finais de semana – Bairro Sinhazinha',
 'Toda sexta e sábado à noite há festas com som alto até as 3h da manhã na Rua Sete de Setembro. Crianças e idosos estão impossibilitados de dormir.',
 'Rua Sete de Setembro', NULL, 'Bairro Sinhazinha', 'Santa Rita do Sapucaí',
 -22.8672, -45.7058,
 NULL, NULL, FALSE,
 'SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO', 'PENDENTE', 'BAIXA',
 TRUE, '749df37cbbf9192d35d01fb047b3415b516849a4e970be0fad2de244e13563fb',
 NULL,
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')

ON CONFLICT (protocol_number) DO NOTHING;


-- ============================================================
-- 3. HISTÓRICO DE STATUS
-- ============================================================
INSERT INTO occurrence_history (occurrence_id, changed_by, old_status, new_status, observation, changed_at)
VALUES
((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260510-A1B2C'),
 (SELECT id FROM users WHERE email = 'carlos@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'Equipe de tapa-buraco agendada para esta semana.',
 NOW() - INTERVAL '20 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260510-A1B2C'),
 (SELECT id FROM users WHERE email = 'carlos@prefeitura.com'),
 'EM_ANDAMENTO', 'ATENDIDA', 'Buraco reparado com massa asfáltica. Serviço concluído.',
 NOW() - INTERVAL '5 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260515-D3E4F'),
 (SELECT id FROM users WHERE email = 'ana@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'Solicitação encaminhada à concessionária. Prazo: 5 dias úteis.',
 NOW() - INTERVAL '3 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260526-S3T4U'),
 (SELECT id FROM users WHERE email = 'ana@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'SAMU acionado. Equipe de assistência social notificada.',
 NOW() - INTERVAL '1 day'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260527-V5W6X'),
 (SELECT id FROM users WHERE email = 'carlos@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'Fiscal de obras notificado para vistoria.',
 NOW() - INTERVAL '25 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260527-V5W6X'),
 (SELECT id FROM users WHERE email = 'carlos@prefeitura.com'),
 'EM_ANDAMENTO', 'ATENDIDA', 'Proprietário autuado. Calçada liberada e tapume instalado.',
 NOW() - INTERVAL '10 days')

ON CONFLICT DO NOTHING;


-- ============================================================
-- 4. REFORÇOS DE OCORRÊNCIA
-- ============================================================
INSERT INTO occurrence_support (occurrence_id, citizen_id, supported_at)
VALUES
((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260510-A1B2C'),
 (SELECT id FROM users WHERE email = 'maria.souza@email.com'), NOW() - INTERVAL '23 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260510-A1B2C'),
 (SELECT id FROM users WHERE email = 'pedro.santos@email.com'), NOW() - INTERVAL '22 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260520-J7K8L'),
 (SELECT id FROM users WHERE email = 'joao.silva@email.com'), NOW() - INTERVAL '6 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260520-J7K8L'),
 (SELECT id FROM users WHERE email = 'pedro.santos@email.com'), NOW() - INTERVAL '5 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-20260518-G5H6I'),
 (SELECT id FROM users WHERE email = 'lucia.lima@email.com'), NOW() - INTERVAL '10 days')

ON CONFLICT DO NOTHING;


-- ============================================================
-- 5. MENSAGENS DE CONTATO
-- ============================================================
INSERT INTO contact_message (name, email, subject, message, created_at)
VALUES
('Roberto Alves', 'roberto.alves@email.com',
 'Dúvida sobre prazo de atendimento',
 'Registrei uma ocorrência há 10 dias (protocolo FC-20260518-G5H6I) e gostaria de saber qual o prazo médio para atendimento.',
 NOW() - INTERVAL '8 days'),

('Fernanda Costa', 'fernanda.costa@email.com',
 'Sugestão de melhoria no aplicativo',
 'Seria muito útil receber uma notificação por e-mail quando o status da minha denúncia for atualizado. Obrigada pelo serviço.',
 NOW() - INTERVAL '3 days'),

('Marcos Oliveira', 'marcos.oliveira@email.com',
 'Como funciona o acompanhamento anônimo?',
 'Fiz uma denúncia anônima e recebi um código de 8 letras. Não encontrei onde inserir esse código. Podem me ajudar?',
 NOW() - INTERVAL '1 day');

