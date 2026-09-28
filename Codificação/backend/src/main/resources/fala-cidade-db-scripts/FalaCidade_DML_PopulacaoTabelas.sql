-- ============================================================
-- FALA, CIDADE! — SCRIPT DE POPULAÇÃO DE TABELAS (SQL-DML)
-- Apêndice D – Visão dos Dados / Modelo Físico
--
-- Cidade de referência: Santa Rita do Sapucaí – MG
-- Todos os dados deste script são FICTÍCIOS, destinados
-- exclusivamente à demonstração e aos testes do sistema.
--
-- SENHAS DAS CONTAS DE SEMENTE
-- Nenhuma credencial é gravada neste arquivo. O marcador
-- ${FALACIDADE_SEED_PASSWORD} é substituído em tempo de
-- execução pelo valor da variável de ambiente de mesmo nome
-- (ver PostgresConnectionManagerConfiguration). Se a variável
-- não estiver definida, a aplicação gera uma senha aleatória
-- e a registra uma única vez no log de inicialização, devendo
-- ser alterada no primeiro acesso.
--
-- Execução manual (fora da aplicação):
--   FALACIDADE_SEED_PASSWORD='<senha>' \
--   envsubst < FalaCidade_DML_PopulacaoTabelas.sql | psql -d fala_cidade
--
-- O script é idempotente: ON CONFLICT DO NOTHING e cláusulas
-- NOT EXISTS permitem reexecução sem duplicar registros.
-- ============================================================

-- ============================================================
-- 0. ENTIDADES DE DOMÍNIO (city, department, classification)
-- ============================================================
INSERT INTO city (name, state) VALUES
('Santa Rita do Sapucaí', 'MG'),
('Pouso Alegre',          'MG'),
('Itajubá',               'MG')
ON CONFLICT (name, state) DO NOTHING;

INSERT INTO department (name, email, city, state) VALUES
('Secretaria de Obras e Serviços Urbanos', 'obras@falacidade.exemplo.br', 'Santa Rita do Sapucaí', 'MG'),
('Secretaria de Meio Ambiente', 'meioambiente@falacidade.exemplo.br', 'Santa Rita do Sapucaí', 'MG'),
('Secretaria de Trânsito e Mobilidade', 'transito@falacidade.exemplo.br', 'Santa Rita do Sapucaí', 'MG'),
('Secretaria de Saúde e Educação', 'saudeeducacao@falacidade.exemplo.br', 'Santa Rita do Sapucaí', 'MG'),
('Secretaria de Assistência Social', 'social@falacidade.exemplo.br', 'Santa Rita do Sapucaí', 'MG'),
('Guarda Municipal e Fiscalização', 'fiscalizacao@falacidade.exemplo.br', 'Santa Rita do Sapucaí', 'MG')
ON CONFLICT (name, city, state) DO NOTHING;

-- As doze classificações correspondem à enumeração OccurrenceType do
-- back-end; a prioridade reproduz a regra de negócio RN04 (RF10).
INSERT INTO classification (code, name, description, priority, requires_sharp_image, department_id) VALUES
('BURACO_NA_RUA_OU_CALCADA', 'Buraco na rua ou calçada',
 'Pavimento ou calçada danificados que ofereçam risco a pedestres e veículos.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Obras e Serviços Urbanos')),
('POSTE_COM_LUZ_QUEIMADA', 'Poste com luz queimada',
 'Falha na iluminação pública em vias, praças e demais logradouros.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Obras e Serviços Urbanos')),
('LIXO_ACUMULADO_OU_TERRENO_SUJO', 'Lixo acumulado ou terreno sujo',
 'Descarte irregular de resíduos e terrenos sem conservação.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Meio Ambiente')),
('SINALIZACAO_OU_SEMAFORO_COM_DEFEITO', 'Sinalização ou semáforo com defeito',
 'Semáforos, placas e faixas em mau funcionamento ou ausentes.', 'ALTA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Trânsito e Mobilidade')),
('PROBLEMAS_EM_PRACAS_E_PARQUES', 'Problemas em praças e parques',
 'Conservação de mobiliário urbano, brinquedos e áreas verdes.', 'BAIXA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Obras e Serviços Urbanos')),
('FALHAS_NO_TRANSPORTE_PUBLICO', 'Falhas no transporte público',
 'Descumprimento de horários, itinerários e condições dos veículos.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Trânsito e Mobilidade')),
('PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA', 'Problemas em posto de saúde ou escola',
 'Infraestrutura e atendimento em unidades de saúde e de ensino.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Saúde e Educação')),
('SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO', 'Som alto ou perturbação do sossego',
 'Ruído excessivo em desacordo com a legislação municipal.', 'BAIXA', FALSE,
 (SELECT id FROM department WHERE name = 'Guarda Municipal e Fiscalização')),
('OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO', 'Obra irregular ou imóvel abandonado',
 'Construções sem alvará, sem tapume ou imóveis sem conservação.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Guarda Municipal e Fiscalização')),
('MAUS_TRATOS_AOS_ANIMAIS', 'Maus-tratos aos animais',
 'Animais em situação de risco, abandono ou violência.', 'ALTA', TRUE,
 (SELECT id FROM department WHERE name = 'Secretaria de Meio Ambiente')),
('PESSOA_PRECISANDO_DE_AJUDA', 'Pessoa precisando de ajuda',
 'Pessoa em situação de rua ou de emergência que demande assistência.', 'ALTA', TRUE,
 (SELECT id FROM department WHERE name = 'Secretaria de Assistência Social')),
('OUTROS_PROBLEMAS', 'Outros problemas',
 'Demandas de zeladoria não contempladas nas demais classificações.', 'MEDIA', FALSE,
 (SELECT id FROM department WHERE name = 'Secretaria de Obras e Serviços Urbanos'))
ON CONFLICT (code) DO NOTHING;


-- ============================================================
-- 1. USUÁRIOS
-- ============================================================
INSERT INTO "user" (fullname, email, password, date_of_birth, phone_number,
                   street, neighborhood, number, cep, city, state,
                   role, is_active, accepts_terms)
VALUES


('Super Administrador do Sistema',
 'admin@falacidade.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 NULL, '35988001100',
 NULL, NULL, NULL, NULL, NULL, NULL,
 'SUPER_ADMIN', TRUE, TRUE),

('Administradora de Santa Rita do Sapucaí',
 'admin.santarita@falacidade.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 NULL, '35988001101',
 NULL, NULL, NULL, NULL, 'Santa Rita do Sapucaí', 'MG',
 'ADMINISTRATOR', TRUE, TRUE),

('Carlos Eduardo Martins',
 'carlos@prefeitura.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 '1985-03-14', '35988002200',
 'Rua Dr. João Pessoa', 'Centro', '210', '37540-000', 'Santa Rita do Sapucaí', 'MG',
 'EMPLOYEE', TRUE, TRUE),

('Ana Beatriz Ferreira',
 'ana@prefeitura.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 '1990-07-22', '35988003300',
 'Av. Prefeito Olavo Gomes de Oliveira', 'Centro', '450', '37540-000', 'Santa Rita do Sapucaí', 'MG',
 'EMPLOYEE', TRUE, TRUE),

('João Carlos Silva',
 'joao.silva@email.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 '1988-11-05', '35991234567',
 'Rua Caetano Moreira da Costa', 'Centro', '78', '37540-000', 'Santa Rita do Sapucaí', 'MG',
 'CITIZEN', TRUE, TRUE),

('Maria Aparecida Souza',
 'maria.souza@email.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 '1995-02-18', '35992345678',
 'Av. Francisco de Paula Quintanilha Ribeiro', 'Bairro Fátima', '132', '37540-000', 'Santa Rita do Sapucaí', 'MG',
 'CITIZEN', TRUE, TRUE),

('Pedro Henrique Santos',
 'pedro.santos@email.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 '1975-08-30', '35993456789',
 'Rua Dr. João Pessoa', 'Centro', '550', '37540-000', 'Santa Rita do Sapucaí', 'MG',
 'CITIZEN', TRUE, TRUE),

('Lúcia Helena Lima',
 'lucia.lima@email.com',
 crypt('${FALACIDADE_SEED_PASSWORD}', gen_salt('bf', 12)),
 '2000-05-12', '35994567890',
 'Rua Sete de Setembro', 'Bairro Sinhazinha', '22', '37540-000', 'Santa Rita do Sapucaí', 'MG',
 'CITIZEN', TRUE, TRUE)

ON CONFLICT (email) DO NOTHING;

UPDATE occurrence o
   SET protocol_number = m.novo
  FROM (VALUES
  ('FC-20260510-A1B2C', 'FC-26-A7B2C'),
  ('FC-20260515-D3E4F', 'FC-26-D3E4F'),
  ('FC-20260518-G5H6I', 'FC-26-G5H6J'),
  ('FC-20260520-J7K8L', 'FC-26-J7K8L'),
  ('FC-20260522-M9N0O', 'FC-26-M9N2P'),
  ('FC-20260525-P1Q2R', 'FC-26-P8Q2R'),
  ('FC-20260526-S3T4U', 'FC-26-S3T4U'),
  ('FC-20260527-V5W6X', 'FC-26-V5W6X'),
  ('FC-20260530-Y7Z8A', 'FC-26-Y7Z8A'),
  ('FC-20260601-B9C0D', 'FC-26-B9C3D')
  ) AS m(antigo, novo)
 WHERE o.protocol_number = m.antigo;

INSERT INTO occurrence (
  protocol_number, title, description,
  street, number, neighborhood, city, state,
  latitude, longitude,
  url_media, cloudinary_public_id, image_blurred,
  type, status, priority,
  is_anonymous, anonymous_tracking_code_hash,
  user_id, created_at, updated_at
)
VALUES

-- 1. CONCLUIDA – buraco na rua com foto nítida
('FC-26-A7B2C',
 'Buraco perigoso na Rua Coronel Antônio Moreira da Costa',
 'Há um buraco de aproximadamente 50 cm de diâmetro e 30 cm de profundidade na Rua Coronel Antônio Moreira da Costa, próximo ao número 78. Já causou queda de motocicleta na semana passada. Situação de risco para pedestres e veículos, especialmente à noite pois não há sinalização.',
 'Rua Coronel Antônio Moreira da Costa', '78', 'Centro', 'Santa Rita do Sapucaí', 'MG',
 -22.25091, -45.70271,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/landscapes/nature-mountains.jpg',
 'samples/landscapes/nature-mountains', FALSE,
 'BURACO_NA_RUA_OU_CALCADA', 'CONCLUIDA', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'joao.silva@email.com'),
 NOW() - INTERVAL '25 days', NOW() - INTERVAL '5 days'),

-- 2. EM_ANDAMENTO – poste sem luz com foto nítida
('FC-26-D3E4F',
 'Poste apagado há duas semanas – Rua José Ribeiro de Barros',
 'O poste localizado na Rua José Ribeiro de Barros, no trecho próximo à Rua Doutor Omar Franqueira, está com a lâmpada queimada há pelo menos 14 dias. À noite o trecho fica completamente escuro, gerando insegurança para os moradores.',
 'Rua José Ribeiro de Barros', NULL, 'Fátima', 'Santa Rita do Sapucaí', 'MG',
 -22.25493, -45.69715,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/people/smiling-man.jpg',
 'samples/people/smiling-man', FALSE,
 'POSTE_COM_LUZ_QUEIMADA', 'EM_ANDAMENTO', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'maria.souza@email.com'),
 NOW() - INTERVAL '20 days', NOW() - INTERVAL '3 days'),

-- 3. PENDENTE – lixo acumulado com foto nítida
('FC-26-G5H6J',
 'Acúmulo de lixo em terreno abandonado – Rua Comendador Custódio Ribeiro',
 'Terreno baldio na Rua Comendador Custódio Ribeiro, próximo ao número 550, acumula lixo doméstico há mais de um mês. O mau cheiro já afeta os estabelecimentos comerciais vizinhos. Há presença de ratos e outros animais no local.',
 'Rua Comendador Custódio Ribeiro', '550', 'Centro', 'Santa Rita do Sapucaí', 'MG',
 -22.25044, -45.70556,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/food/dessert.jpg',
 'samples/food/dessert', FALSE,
 'LIXO_ACUMULADO_OU_TERRENO_SUJO', 'PENDENTE', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'pedro.santos@email.com'),
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),

-- 4. PENDENTE – semáforo com defeito – ALTA – sem foto
('FC-26-J7K8L',
 'Semáforo piscando em amarelo – Av. Barão do Rio Branco',
 'O semáforo da Av. Barão do Rio Branco, junto à Praça Santa Rita, está funcionando apenas no modo piscante amarelo desde domingo. Trata-se de um cruzamento de alto fluxo com dois quase-acidentes hoje.',
 'Avenida Barão do Rio Branco', NULL, 'Centro', 'Santa Rita do Sapucaí', 'MG',
 -22.25164, -45.70416,
 NULL, NULL, FALSE,
 'SINALIZACAO_OU_SEMAFORO_COM_DEFEITO', 'PENDENTE', 'ALTA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'lucia.lima@email.com'),
 NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),

-- 5. PENDENTE – praça – BAIXA – sem foto
('FC-26-M9N2P',
 'Brinquedos quebrados na Praça Santa Rita',
 'Os brinquedos instalados na Praça Santa Rita estão em condições precárias: balanço sem assento, escorregador com fresta de metal exposta e gira-gira travado. Risco para crianças.',
 'Praça Santa Rita', NULL, 'Centro', 'Santa Rita do Sapucaí', 'MG',
 -22.25222, -45.70360,
 NULL, NULL, FALSE,
 'PROBLEMAS_EM_PRACAS_E_PARQUES', 'PENDENTE', 'BAIXA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'joao.silva@email.com'),
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),

-- 6. PENDENTE – maus tratos – ALTA – foto com flag de BORRADA (image_blurred=TRUE)
('FC-26-P8Q2R',
 'Denúncia de maus tratos a cão – Rua Aprigio Rodrigues',
 'Vizinho mantém um cão de grande porte acorrentado em espaço mínimo, sem abrigo, ração ou água. Animal apresenta costelas visíveis indicando desnutrição grave. Caso precisa de atendimento urgente da vigilância animal.',
 'Rua Aprigio Rodrigues', '22', 'Eletrônica', 'Santa Rita do Sapucaí', 'MG',
 -22.25747, -45.70275,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/animals/cat.jpg',
 'samples/animals/cat', TRUE,
 'MAUS_TRATOS_AOS_ANIMAIS', 'PENDENTE', 'ALTA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'lucia.lima@email.com'),
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),

-- 7. EM_ANDAMENTO – ANÔNIMA – pessoa em risco – ALTA – sem foto
-- Código de rastreamento: B7KN4PX2 | SHA-256: 7e96f243ce080c309643afa5adfd36bcb83eecd3ded45ed1319e39347b9f00ac
('FC-26-S3T4U',
 'Pessoa em estado grave próximo à rodoviária',
 'Uma pessoa está deitada na calçada da Alameda José Cleto Duarte, em frente à rodoviária, aparentemente inconsciente. Apresenta tremores e não responde a estímulos.',
 'Alameda José Cleto Duarte', NULL, 'Centro', 'Santa Rita do Sapucaí', 'MG',
 -22.25414, -45.70443,
 NULL, NULL, FALSE,
 'PESSOA_PRECISANDO_DE_AJUDA', 'EM_ANDAMENTO', 'ALTA',
 TRUE, '7e96f243ce080c309643afa5adfd36bcb83eecd3ded45ed1319e39347b9f00ac',
 NULL,
 NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),

-- 8. CONCLUIDA – obra irregular com foto nítida
('FC-26-V5W6X',
 'Obra sem tapume bloqueando calçada – Av. Coronel João Euzébio',
 'Construção na Av. Coronel João Euzébio sem tapume de proteção e com entulho na calçada, obrigando pedestres a caminhar na rua. Obra sem alvará visível.',
 'Avenida Coronel João Euzébio', '120', 'Centro', 'Santa Rita do Sapucaí', 'MG',
 -22.24949, -45.70301,
 'https://res.cloudinary.com/demo/image/upload/w_800,c_fill/samples/imagecon-group.jpg',
 'samples/imagecon-group', FALSE,
 'OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO', 'CONCLUIDA', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'maria.souza@email.com'),
 NOW() - INTERVAL '30 days', NOW() - INTERVAL '10 days'),

-- 9. PENDENTE – transporte público – sem foto
('FC-26-Y7Z8A',
 'Ônibus linha 03 não cumpre horário há semanas',
 'O ônibus da linha 03 está atrasando entre 40 e 60 minutos nos horários de pico. Trabalhadores chegam atrasados e crianças perdem aula. Situação se repete há três semanas.',
 'Rua Cincinato Marquês Pereira', NULL, 'Rua Nova', 'Santa Rita do Sapucaí', 'MG',
 -22.25164, -45.70066,
 NULL, NULL, FALSE,
 'FALHAS_NO_TRANSPORTE_PUBLICO', 'PENDENTE', 'MEDIA',
 FALSE, NULL,
 (SELECT id FROM "user" WHERE email = 'pedro.santos@email.com'),
 NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),

-- 10. PENDENTE – ANÔNIMA – barulho – BAIXA – sem foto
-- Código de rastreamento: M3RP6TW9 | SHA-256: 749df37cbbf9192d35d01fb047b3415b516849a4e970be0fad2de244e13563fb
('FC-26-B9C3D',
 'Som alto todos os finais de semana – Casa de Vítor',
 'Toda sexta e sábado à noite há festas com som alto até as 3h da manhã na Rua Padre Vitor. Crianças e idosos estão impossibilitados de dormir.',
 'Rua Padre Vitor', NULL, 'Casa de Vítor', 'Santa Rita do Sapucaí', 'MG',
 -22.24779, -45.70576,
 NULL, NULL, FALSE,
 'SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO', 'PENDENTE', 'BAIXA',
 TRUE, '749df37cbbf9192d35d01fb047b3415b516849a4e970be0fad2de244e13563fb',
 NULL,
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')

-- Endereço e coordenadas conferidos no OpenStreetMap. O DO UPDATE corrige os
-- bancos populados com a versão anterior, cujos pontos caíam fora do município;
-- status e datas ficam como estão.
ON CONFLICT (protocol_number) DO UPDATE SET
  title        = EXCLUDED.title,
  description  = EXCLUDED.description,
  street       = EXCLUDED.street,
  number       = EXCLUDED.number,
  neighborhood = EXCLUDED.neighborhood,
  latitude     = EXCLUDED.latitude,
  longitude    = EXCLUDED.longitude;


-- ============================================================
-- 3. HISTÓRICO DE STATUS
-- ============================================================
-- A tabela não tem chave natural, então ON CONFLICT nunca disparava e cada
-- subida do backend duplicava o histórico; o NOT EXISTS é que evita isso.
INSERT INTO occurrence_history (occurrence_id, changed_by, old_status, new_status, observation, changed_at)
SELECT * FROM (VALUES
((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-A7B2C'),
 (SELECT id FROM "user" WHERE email = 'carlos@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'Equipe de tapa-buraco agendada para esta semana.',
 NOW() - INTERVAL '20 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-A7B2C'),
 (SELECT id FROM "user" WHERE email = 'carlos@prefeitura.com'),
 'EM_ANDAMENTO', 'CONCLUIDA', 'Buraco reparado com massa asfáltica. Serviço concluído.',
 NOW() - INTERVAL '5 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-D3E4F'),
 (SELECT id FROM "user" WHERE email = 'ana@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'Solicitação encaminhada à concessionária. Prazo: 5 dias úteis.',
 NOW() - INTERVAL '3 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-S3T4U'),
 (SELECT id FROM "user" WHERE email = 'ana@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'SAMU acionado. Equipe de assistência social notificada.',
 NOW() - INTERVAL '1 day'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-V5W6X'),
 (SELECT id FROM "user" WHERE email = 'carlos@prefeitura.com'),
 'PENDENTE', 'EM_ANDAMENTO', 'Fiscal de obras notificado para vistoria.',
 NOW() - INTERVAL '25 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-V5W6X'),
 (SELECT id FROM "user" WHERE email = 'carlos@prefeitura.com'),
 'EM_ANDAMENTO', 'CONCLUIDA', 'Proprietário autuado. Calçada liberada e tapume instalado.',
 NOW() - INTERVAL '10 days')
) AS seed(occurrence_id, changed_by, old_status, new_status, observation, changed_at)
WHERE seed.occurrence_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM occurrence_history h
                   WHERE h.occurrence_id = seed.occurrence_id
                     AND h.new_status    = seed.new_status
                     AND h.observation   = seed.observation);

-- Remove as cópias que as subidas anteriores deixaram, mantendo a primeira.
-- Só nas ocorrências do seed: a data de cada cópia difere (NOW() da subida).
DELETE FROM occurrence_history h
 USING occurrence_history d, occurrence o
 WHERE h.occurrence_id = d.occurrence_id
   AND h.new_status    = d.new_status
   AND h.observation   = d.observation
   AND h.id > d.id
   AND o.id = h.occurrence_id
   AND o.protocol_number IN ('FC-26-A7B2C', 'FC-26-D3E4F', 'FC-26-S3T4U', 'FC-26-V5W6X');


-- ============================================================
-- 4. REFORÇOS DE OCORRÊNCIA
-- ============================================================
INSERT INTO occurrence_support (occurrence_id, citizen_id, supported_at)
VALUES
((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-A7B2C'),
 (SELECT id FROM "user" WHERE email = 'maria.souza@email.com'), NOW() - INTERVAL '23 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-A7B2C'),
 (SELECT id FROM "user" WHERE email = 'pedro.santos@email.com'), NOW() - INTERVAL '22 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-J7K8L'),
 (SELECT id FROM "user" WHERE email = 'joao.silva@email.com'), NOW() - INTERVAL '6 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-J7K8L'),
 (SELECT id FROM "user" WHERE email = 'pedro.santos@email.com'), NOW() - INTERVAL '5 days'),

((SELECT id FROM occurrence WHERE protocol_number = 'FC-26-G5H6J'),
 (SELECT id FROM "user" WHERE email = 'lucia.lima@email.com'), NOW() - INTERVAL '10 days')

ON CONFLICT DO NOTHING;


-- ============================================================
-- 5. MENSAGENS DE CONTATO
-- ============================================================
INSERT INTO contact_message (name, email, subject, message, created_at)
SELECT * FROM (VALUES
('Roberto Alves', 'roberto.alves@email.com',
 'Dúvida sobre prazo de atendimento',
 'Registrei uma ocorrência há 10 dias (protocolo FC-26-G5H6J) e gostaria de saber qual o prazo médio para atendimento.',
 NOW() - INTERVAL '8 days'),

('Fernanda Costa', 'fernanda.costa@email.com',
 'Sugestão de melhoria no aplicativo',
 'Seria muito útil receber uma notificação por e-mail quando o status da minha denúncia for atualizado. Obrigada pelo serviço.',
 NOW() - INTERVAL '3 days'),

('Marcos Oliveira', 'marcos.oliveira@email.com',
 'Como funciona o acompanhamento anônimo?',
 'Fiz uma denúncia anônima e recebi um código de 8 letras. Não encontrei onde inserir esse código. Podem me ajudar?',
 NOW() - INTERVAL '1 day')
) AS seed(name, email, subject, message, created_at)
WHERE NOT EXISTS (SELECT 1 FROM contact_message);


-- ============================================================
-- 6. VÍNCULOS COM AS ENTIDADES DE DOMÍNIO
-- Preenche as chaves estrangeiras city_id, classification_id e
-- department_id a partir dos valores textuais já persistidos.
-- ============================================================
UPDATE "user" u
   SET city_id = c.id
  FROM city c
 WHERE u.city_id IS NULL AND u.city = c.name AND c.state = 'MG';

UPDATE "user" u
   SET department_id = (SELECT id FROM department WHERE name = 'Secretaria de Obras e Serviços Urbanos')
 WHERE u.department_id IS NULL AND u.role = 'EMPLOYEE';

UPDATE occurrence o
   SET city_id = c.id
  FROM city c
 WHERE o.city_id IS NULL AND o.city = c.name AND c.state = 'MG';

UPDATE occurrence o
   SET classification_id = cl.id,
       department_id     = cl.department_id
  FROM classification cl
 WHERE o.classification_id IS NULL AND o.type = cl.code;
