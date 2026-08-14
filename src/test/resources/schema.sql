-- GENERATED FILE — do not edit by hand.
-- Regenerated from drizzle/*.sql via: npm run sync:test-schema

-- ===== 0000_normal_captain_midlands.sql =====
CREATE TYPE "public"."nivel_questao" AS ENUM('FACIL', 'MODERADO', 'DIFICIL');--> statement-breakpoint
CREATE TYPE "public"."tipo_questao" AS ENUM('CERTO_ERRADO', 'MULTIPLA_ESCOLHA');--> statement-breakpoint
CREATE TABLE "questao" (
	"source_id" text PRIMARY KEY NOT NULL,
	"tipo" "tipo_questao" NOT NULL,
	"enunciado" text NOT NULL,
	"opcao_a" text,
	"opcao_b" text,
	"opcao_c" text,
	"opcao_d" text,
	"opcao_e" text,
	"gabarito" text NOT NULL,
	"nivel" "nivel_questao",
	"banca" text NOT NULL,
	"orgao" text NOT NULL,
	"cargo" text NOT NULL,
	"ano" smallint,
	"materia" text NOT NULL,
	"assunto" text NOT NULL,
	"comentario" text,
	"texto_ref" text,
	"figura_ref" text,
	"imported_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE INDEX "idx_questao_banca" ON "questao" USING btree ("banca");--> statement-breakpoint
CREATE INDEX "idx_questao_orgao" ON "questao" USING btree ("orgao");--> statement-breakpoint
CREATE INDEX "idx_questao_materia" ON "questao" USING btree ("materia");--> statement-breakpoint
CREATE INDEX "idx_questao_assunto" ON "questao" USING btree ("assunto");--> statement-breakpoint
CREATE INDEX "idx_questao_ano" ON "questao" USING btree ("ano");--> statement-breakpoint
CREATE INDEX "idx_questao_tipo" ON "questao" USING btree ("tipo");--> statement-breakpoint
CREATE INDEX "idx_questao_nivel" ON "questao" USING btree ("nivel");

-- ===== 0001_flowery_devos.sql =====
CREATE TABLE "banca" (
	"id" serial PRIMARY KEY NOT NULL,
	"nome" text NOT NULL,
	CONSTRAINT "banca_nome_unique" UNIQUE("nome")
);
--> statement-breakpoint
CREATE TABLE "disciplina" (
	"id" serial PRIMARY KEY NOT NULL,
	"nome" text NOT NULL,
	CONSTRAINT "disciplina_nome_unique" UNIQUE("nome")
);
--> statement-breakpoint
CREATE TABLE "orgao" (
	"id" serial PRIMARY KEY NOT NULL,
	"nome" text NOT NULL,
	CONSTRAINT "orgao_nome_unique" UNIQUE("nome")
);
--> statement-breakpoint
CREATE TABLE "questao_disciplina" (
	"source_id" text NOT NULL,
	"disciplina_id" integer NOT NULL,
	CONSTRAINT "questao_disciplina_source_id_disciplina_id_pk" PRIMARY KEY("source_id","disciplina_id")
);
--> statement-breakpoint
ALTER TABLE "questao" ADD COLUMN "banca_id" integer;--> statement-breakpoint
ALTER TABLE "questao" ADD COLUMN "orgao_id" integer;--> statement-breakpoint
ALTER TABLE "questao_disciplina" ADD CONSTRAINT "questao_disciplina_source_id_questao_source_id_fk" FOREIGN KEY ("source_id") REFERENCES "public"."questao"("source_id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "questao_disciplina" ADD CONSTRAINT "questao_disciplina_disciplina_id_disciplina_id_fk" FOREIGN KEY ("disciplina_id") REFERENCES "public"."disciplina"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "questao" ADD CONSTRAINT "questao_banca_id_banca_id_fk" FOREIGN KEY ("banca_id") REFERENCES "public"."banca"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "questao" ADD CONSTRAINT "questao_orgao_id_orgao_id_fk" FOREIGN KEY ("orgao_id") REFERENCES "public"."orgao"("id") ON DELETE no action ON UPDATE no action;

-- ===== 0002_lethal_hardball.sql =====
CREATE TABLE "roles" (
	"id" serial PRIMARY KEY NOT NULL,
	"authority" text NOT NULL,
	CONSTRAINT "roles_authority_unique" UNIQUE("authority")
);
--> statement-breakpoint
CREATE TABLE "user_role_junction" (
	"user_id" uuid NOT NULL,
	"role_id" integer NOT NULL,
	CONSTRAINT "user_role_junction_user_id_role_id_pk" PRIMARY KEY("user_id","role_id")
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"first_name" text NOT NULL,
	"last_name" text NOT NULL,
	"email" text NOT NULL,
	"password" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "users_email_unique" UNIQUE("email")
);
--> statement-breakpoint
ALTER TABLE "questao" ADD COLUMN "id" uuid DEFAULT gen_random_uuid() NOT NULL;--> statement-breakpoint
ALTER TABLE "user_role_junction" ADD CONSTRAINT "user_role_junction_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_role_junction" ADD CONSTRAINT "user_role_junction_role_id_roles_id_fk" FOREIGN KEY ("role_id") REFERENCES "public"."roles"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "questao" ADD CONSTRAINT "questao_id_unique" UNIQUE("id");

-- ===== 0003_question_uniqueness.sql =====
CREATE FUNCTION q_content_key(
  p_enunciado text, p_a text, p_b text, p_c text, p_d text, p_e text
) RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT lower(regexp_replace(trim(p_enunciado), '\s+', ' ', 'g'))
       || chr(30)
       || coalesce(
            (SELECT string_agg(n, chr(31) ORDER BY n)
             FROM (
               SELECT lower(regexp_replace(trim(o), '\s+', ' ', 'g')) AS n
               FROM unnest(ARRAY[p_a, p_b, p_c, p_d, p_e]) AS t(o)
               WHERE o IS NOT NULL
             ) s),
            '')
$$;
--> statement-breakpoint
DELETE FROM questao
WHERE q_content_key(enunciado, opcao_a, opcao_b, opcao_c, opcao_d, opcao_e) IN (
  SELECT q_content_key(enunciado, opcao_a, opcao_b, opcao_c, opcao_d, opcao_e)
  FROM questao
  GROUP BY 1
  HAVING count(DISTINCT lower(trim(gabarito))) > 1
);
--> statement-breakpoint
DELETE FROM questao
WHERE source_id IN (
  SELECT source_id FROM (
    SELECT source_id,
           row_number() OVER (
             PARTITION BY q_content_key(enunciado, opcao_a, opcao_b, opcao_c, opcao_d, opcao_e)
             ORDER BY imported_at DESC, source_id DESC
           ) AS rn
    FROM questao
  ) r
  WHERE r.rn > 1
);
--> statement-breakpoint
ALTER TABLE questao
  ADD COLUMN content_key text
  GENERATED ALWAYS AS (q_content_key(enunciado, opcao_a, opcao_b, opcao_c, opcao_d, opcao_e)) STORED;
--> statement-breakpoint
CREATE UNIQUE INDEX uq_questao_content_key ON questao (content_key);

-- ===== 0004_faithful_hiroim.sql =====
CREATE TABLE "refresh_tokens" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"token_hash" text NOT NULL,
	"family_id" uuid NOT NULL,
	"issued_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"revoked_at" timestamp with time zone,
	"replaced_by" uuid,
	CONSTRAINT "refresh_tokens_token_hash_unique" UNIQUE("token_hash")
);
--> statement-breakpoint
ALTER TABLE "refresh_tokens" ADD CONSTRAINT "refresh_tokens_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_refresh_user" ON "refresh_tokens" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "idx_refresh_family" ON "refresh_tokens" USING btree ("family_id");

-- ===== test seed (roles) =====
INSERT INTO roles (authority) VALUES ('USER'), ('ADMIN') ON CONFLICT (authority) DO NOTHING;
