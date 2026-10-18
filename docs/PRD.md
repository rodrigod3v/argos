# PRD — Argos

**Versão:** 2.0 (local-first)
**Data:** 2026-10-18
**Status:** aprovado para POC
**Substitui:** v1.0 (cloud/BYOK) — descartada

---

## 1. Visão

Argos é um assistente de voz que roda **inteiro dentro do celular**. Fala-se, ele age.
Não existe servidor, conta, chave de API nem rede no caminho.

O produto é **dois artefatos**: o aparelho e o app. Nada mais.

## 2. Regra dura (a que manda em tudo)

> **Só o aparelho e o app. O app se sustenta sozinho.**

Desdobramentos não negociáveis:

| proibido | por quê |
|---|---|
| backend / proxy / worker | vira dependência externa e custo |
| conta, login, chave de API | quebra o "só o app" e expõe segredo |
| LLM em nuvem | idem |
| STT em nuvem | idem |
| download de modelo em runtime | o app precisa funcionar sem rede desde o primeiro uso |
| permissão `INTERNET` | é a prova auditável de que nada sai |

Custo zero deixa de ser meta financeira e passa a ser **consequência da arquitetura**.
Local não é uma otimização: é o produto.

## 3. Usuário e teto de hardware

Aparelho-alvo real: **Moto G6 Plus** — Android 9 (API 28), arm64, **2,8 GB RAM**,
Snapdragon 630 (8× Cortex-A53), Adreno 508.

Esse teto define o que é possível. Nada de "depois otimiza".

### 3.1 O que cabe

| peça | tamanho | viável? |
|---|---|---|
| Vosk `vosk-android` (AAR) | 13,5 MB | sim |
| Vosk `vosk-model-small-pt-0.3` | **31 MB** | sim |
| TTS do sistema | 0 | sim |
| AccessibilityService | 0 | sim |
| APK total estimado | ~50 MB | sim |

### 3.2 O que **não** cabe — e é preciso dizer na cara

Um LLM local **não é viável para uso interativo neste aparelho**.

- Qwen2.5-0.5B Q4 ≈ 400 MB. Cabe em disco, mas divide 2,8 GB com Android 9 + Google Play
  Services, que já comem ~1,5 GB.
- Snapdragon 630 roda ~3–6 tok/s nesse tamanho. Um JSON de ação tem ~30 tokens →
  **5 a 10 segundos** por comando.
- Assistente de voz que pensa 8 s não é assistente.

**Conclusão de projeto:** o cérebro do Argos **não é um modelo**. É um resolvedor
determinístico + fuzzy. Isso não é um downgrade — é o que torna o produto possível.

LLM local fica como *opção* para aparelhos com RAM sobrando (≥ 6 GB), atrás de uma porta
que já existe. Nunca como requisito, nunca como caminho do POC.

## 4. Por que determinístico ganha aqui

O trabalho real não é conversa livre. É:

```
"abrir o prumo"      → verbo + nome de app
"toca em entrar"     → verbo + texto na tela
"digita 1234"        → verbo + valor
"volta"              → global
```

Isso é **casamento de padrão**, não raciocínio. Um resolvedor:

- responde em **< 50 ms** (LLM levaria segundos);
- funciona **offline**;
- tem resultado **previsível e depurável** (você lê a regra que falhou);
- custa **0 byte** de RAM além do código.

Um LLM só ganharia em comando multi-passo aberto ("vai em pedidos, filtra por hoje, abre o
primeiro"). Isso é **P2**, resolvido melhor com *skills* nomeadas do que com inferência.

## 5. Escopo

### P0 — POC (esta entrega)
- [x] `AccessibilityService` como mãos: tocar, digitar, voltar, home, notificações
- [x] Tier 0: verbo + argumento, normalização sem acento
- [x] Resolução de app por nome (`"prumo"` → `br.com.cod3x.prumomock`)
- [x] Snapshot da tela em texto (para fuzzy e para depuração)
- [x] TTS de resposta
- [ ] **STT offline via Vosk** — substitui o serviço do Google
- [ ] **Wake word offline** via Vosk com gramática restrita
- [ ] Tier 1 local: fuzzy resolver sobre nomes de app e nós de tela
- [ ] Manter `INTERNET` fora do manifest

### P1
- Sinônimos e conjugações ("abre/abrir/abri/lança/entra no")
- Desambiguação por contexto ("toca em confirmar" com dois botões → o visível)
- Skills nomeadas ("modo entrega" = sequência fixa de ações)
- Redução do snapshot antes do match (só nós interativos)

### P2 (opcional, por aparelho)
- LLM local (llama.cpp) atrás da porta `CommandPlanner`, ativado só se houver RAM
- VLM local para telas sem node tree (jogo, canvas)

## 6. Não-objetivos

- Conversa livre / conhecimento geral. Argos **opera o aparelho**, não responde trivia.
- Nuvem, em qualquer forma.
- Play Store (política hostil a `QUERY_ALL_PACKAGES` + acessibilidade).
- iOS.

## 7. Arquitetura

Dependência aponta para dentro. `core/` e `domain/` não importam Android.

```
core/     puro     modelos · norm() · WakePhrase · CommandRouter
domain/   puro     portas + caso de uso HandleCommand
data/     Android  PackageManager · Accessibility · Vosk STT · TTS
entry/    Android  Application · AccessibilityService · foreground service · Activity
di/       Android  container manual
```

### 7.1 Pipeline — 100% local

```
mic
 └─▶ Vosk KWS (gramática: ["argos"])          ~0 CPU até ouvir a palavra
      └─▶ Vosk ASR (vosk-model-small-pt-0.3)  streaming, offline
           └─▶ WakePhrase.extract()
                └─▶ CommandRouter (Tier 0)
                     ├─ casou ──▶ HandleCommand ──▶ AccessibilityService
                     └─ Unknown
                          └─▶ FuzzyResolver (Tier 1 local)
                               └─▶ HandleCommand
                                    └─▶ TTS do sistema
```

Nenhuma seta sai do aparelho. Nenhuma permissão de rede existe.

### 7.2 A porta `CommandPlanner`

Já desenhada no código. Hoje sem implementação. Amanhã pode receber:

- `LocalFuzzyPlanner` — P0, default, zero model
- `LocalLlmPlanner` — P2, llama.cpp, só em aparelho com RAM

Trocar um pelo outro não toca em `HandleCommand` nem na camada de voz. É por isso que a
porta existe.

## 8. Privacidade

Não há o que vazar: o app não tem permissão de rede. Áudio, texto de tela e comandos não
saem do processo. A garantia é **estrutural**, não uma promessa de política.

## 9. Riscos

| risco | impacto | mitigação |
|---|---|---|
| Vosk impreciso em pt-BR ruidoso | alto | modelo small como baseline; caminho aberto para large (1,6 GB) em aparelho melhor |
| Vosk não acompanha tempo real no A53 | médio | small model; medição no POC; se travar, degradar para push-to-talk |
| RAM apertada (2,8 GB) | médio | Vosk pequeno (~200 MB pico), modelo carregado só durante escuta |
| mic aberto 24/7 consome bateria | alto | KWS com gramática restrita; serviço para quando a tela apaga (P1) |
| acessibilidade desligada pelo sistema | médio | detectar e instruir na UI |
| fuzzy casar alvo errado | alto | nunca executar sem 1 candidato claro; ambíguo → pedir confirmação |
| ação irreversível por engano | alto | política no `HandleCommand`; confirmar antes de enviar/apagar |

## 10. Métricas

POC:
- `"Argos abrir o Prumo"` → app aberto em **< 1,5 s** após a fala. *(Tier 0 já atingido; falta trocar o STT)*
- STT offline reconhece o comando em **≥ 8 de 10** tentativas, sem rede.
- Latência de transcrição **< 1,5 s** para frase de ~3 s.
- Pico de RAM do serviço **< 350 MB**.
- **0 requisições de rede. 0 permissões de rede.**

### 10.1 Como provar o "zero rede"

Auditoria objetiva, no aparelho:

```
aapt dump permissions app-debug.apk        # não deve listar INTERNET
adb shell dumpsys package com.argos | grep -i internet   # vazio
```

Se um dia `INTERNET` aparecer, o produto deixou de ser o que este PRD define.

## 11. Critérios de aceite do POC

1. Com o aparelho em modo avião, `"Argos abrir o Prumo"` funciona.
2. Com o aparelho em modo avião, `"Argos toca em entrar"` funciona.
3. Sem rede e sem Google app, o STT continua transcrevendo.
4. `INTERNET` ausente do manifest.
5. APK ≤ 60 MB.
6. Nenhum crash em 100 comandos.
7. Nenhum arquivo de modelo baixado em runtime.

## 12. Roadmap

| fase | entrega | saída |
|---|---|---|
| POC | Vosk offline + fuzzy | critérios da seção 11, em modo avião |
| P1 | KWS dedicado + skills | bateria aceitável em 8 h |
| P2 | LLM local opcional | só em aparelho ≥ 6 GB |

## 13. Decisões abertas

- Vosk small basta, ou vale testar o large (1,6 GB) num aparelho melhor como referência?
- Wake word: gramática Vosk (zero download) ou Porcupine (melhor recall, modelo .ppn extra)?
- O serviço deve parar com a tela apagada? Perde "mãos livres", ganha bateria.
- Push-to-talk como fallback quando o KWS falhar três vezes seguidas?
