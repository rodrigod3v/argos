# POC — Argos local

**Objetivo:** provar que um celular sozinho — sem rede, sem conta, sem chave, sem servidor —
transforma fala em ação sobre a tela.

**Alvo de teste:** Moto G6 Plus · Android 9 (API 28) · arm64 · 2,8 GB RAM · Snapdragon 630.

---

## 1. Hipótese

> Um Android sozinho consegue ouvir, entender e operar a própria tela, offline, com
> latência aceitável e sem modelo de linguagem.

Se a hipótese cair, o produto cai. Se subir, o resto é refinamento.

## 2. O que este POC prova

| # | Prova |
|---|---|
| 1 | STT offline em pt-BR funciona neste hardware |
| 2 | Wake word pode ser detectada sem serviço de nuvem |
| 3 | Um resolvedor determinístico cobre os comandos reais |
| 4 | O app abre outros apps e toca em elementos por voz |
| 5 | Tudo isso com `INTERNET` ausente do manifest |

## 3. O que este POC **não** prova

- Comando multi-passo aberto. Precisa de skill ou de LLM (P2).
- Precisão de STT em ambiente muito ruidoso.
- Bateria em uso de 8 h contínuas.
- Conversa livre. Fora de escopo por definição.

## 4. Componentes (verificados em 2026-10)

| peça | versão | tamanho | licença |
|---|---|---|---|
| `com.alphacephei:vosk-android` | **0.3.75** | AAR 13,5 MB | Apache 2.0 |
| modelo `vosk-model-small-pt-0.3` | 0.3 | **31 MB** | Apache 2.0 |
| TTS | `android.speech.tts.TextToSpeech` | 0 | sistema |
| STT | — | — | substitui `SpeechRecognizer` |

O modelo entra **no APK** (assets), não é baixado em runtime. Requisito da regra dura.

Alternativa medida depois: `vosk-model-pt-fb-v0.1.1` (1,6 GB) só como referência de
qualidade, nunca no build.

## 5. Pipeline

```
AudioRecord 16 kHz mono PCM
 └─▶ Vosk Recognizer A — gramática ["argos", "[unk]"]
      │  mantém o CPU quase parado até ouvir a wake word
      └─▶ detectou "argos"
           └─▶ Vosk Recognizer B — vocabulário livre (pt-BR)
                └─▶ texto final
                     └─▶ WakePhrase.extract()        (já existe, puro)
                          └─▶ CommandRouter          (já existe, puro)
                               ├─ conhecido → HandleCommand → AccessibilityService
                               └─ Unknown → FuzzyResolver → HandleCommand
                                    └─▶ TextToSpeech
```

Dois `Recognizer` no mesmo `Model` (memória compartilhada). O A escuta barato; o B só
liga quando o A dispara.

## 6. Mudanças de código

A arquitetura atual **não muda**. Troca-se a implementação de duas portas.

```
data/voice/VoskSpeechInput.kt        novo — implementa SpeechInput
data/voice/VoskWakeSpotter.kt        novo — KWS com gramática
data/voice/AndroidSpeechInput.kt     mantido como fallback
core/fuzzy/FuzzyResolver.kt          novo — puro, testável em JVM
domain/port/CommandPlanner.kt        novo — porta do Tier 1
data/plan/LocalFuzzyPlanner.kt       novo — implementa a porta
di/Graph.kt                          troca o bind
entry/ArgosVoiceService.kt           liga o spotter ao input
```

`core/`, `domain/` e `entry/` continuam como estão. É o teste de que a separação valeu.

### 6.1 Dependência

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75")
}
```

### 6.2 Modelo no APK

```
app/src/main/assets/vosk/vosk-model-small-pt-0.3/   (31 MB, extraído no 1o run para filesDir)
```

Primeira execução copia de `assets` para `filesDir/vosk/` (Vosk precisa de caminho real de
arquivo). Cópia única, ~1 s, sem rede.

## 7. Contrato do resolvedor (Tier 1)

Entrada:

```json
{
  "verb": "toca",
  "query": "entrar",
  "apps": ["Prumo Entregador", "Calculadora", "Chrome"],
  "screen": [
    "12 Button [0,1200-1080,1320] clickable \"Entrar\"",
    "14 Button [0,1340-1080,1460] clickable \"Criar conta\""
  ]
}
```

Saída: um `Command` do vocabulário fechado, ou `null`.

Regra de decisão:

1. match exato normalizado → executa
2. prefixo único → executa
3. único `contains` → executa
4. 2+ candidatos → **não age**, pede repetição com a lista
5. 0 candidatos → "não achei"

Nunca chuta. Ambiguidade vira pergunta, não ação.

## 8. Plano de teste

Pré-requisito: aparelho em **modo avião** durante todo o teste.

```bash
# 1. sem permissão de rede, por construção
aapt dump permissions app-debug.apk | grep -i internet
# esperado: vazio

# 2. instala
adb install -r app-debug.apk

# 3. modelo presente
adb shell run-as com.argos ls files/vosk/vosk-model-small-pt-0.3
# esperado: am/, conf/, graph/, ivector/ ...

# 4. modo avião
adb shell cmd connectivity airplane-mode enable

# 5. serviço
adb shell am start -n com.argos/.entry.MainActivity --ez voice true
adb shell dumpsys activity services com.argos | grep isForeground
# esperado: isForeground=true

# 6. fala → observa
adb logcat -c && adb logcat -s Argos
# falar: "Argos abrir o prumo"
# esperado: Heard/"Abrindo Prumo Entregador."
adb shell dumpsys window | grep mCurrentFocus
# esperado: br.com.cod3x.prumomock/...
```

Repetir para:

| fala | esperado |
|---|---|
| `Argos abrir o chrome` | `com.android.chrome` em foco |
| `Argos toca em entrar` | tap no nó "Entrar" |
| `Argos digita teste` | texto no campo focado |
| `Argos voltar` | tela anterior |
| `Argos home` | launcher |
| `Argos conversa fiada` | "não entendi", sem crash |

## 9. Métricas de aceite

| métrica | meta | como medir |
|---|---|---|
| latência fala→ação | < 1,5 s | timestamp no logcat |
| acerto de STT | ≥ 8/10 | 10 repetições da mesma frase |
| pico de RAM do serviço | < 350 MB | `adb shell dumpsys meminfo com.argos` |
| APK | ≤ 60 MB | tamanho do arquivo |
| rede | 0 requisição | modo avião + ausência de `INTERNET` |
| crash | 0 em 100 | contador manual |

## 10. Plano B (se falhar)

| falha | sintoma | plano B |
|---|---|---|
| Vosk não acompanha tempo real | transcrição atrasa e acumula | push-to-talk: grava, transcreve em bloco, executa |
| precisão ruim em pt-BR | erra "prumo" com frequência | modelo large (1,6 GB) se o aparelho trocar; ou gramática restrita por app |
| RAM estoura | serviço morto pelo sistema | descarregar `Model` quando ocioso > 60 s |
| KWS com falso positivo | acorda sozinho | exigir 2 sílabas + confiança mínima |

Push-to-talk é o fallback honesto: perde "mãos livres", mantém o produto de pé.

## 11. Orçamento

| recurso | valor |
|---|---|
| disco (código + modelo) | ~50 MB |
| RAM em escuta | ~200–300 MB |
| RAM ocioso | ~40 MB |
| CPU em escuta | baixo (KWS restrito); sobe só na transcrição |
| rede | **0** |
| dinheiro | **R$ 0,00** |

## 12. Sequência

1. Adicionar a AAR e empacotar o modelo small em assets
2. `VoskModelInstaller` — copia assets → filesDir no primeiro run
3. `VoskSpeechInput : SpeechInput` — substitui o `AndroidSpeechInput` no `Graph`
4. Medir latência e RAM com o pipeline atual (Tier 0 já funciona)
5. `FuzzyResolver` + `LocalFuzzyPlanner` para o Tier 1
6. `VoskWakeSpotter` — gramática `["argos", "[unk]"]`
7. Rodar o plano de teste em modo avião

Passo 4 já entrega valor: só trocar o STT remove o Google do caminho.

---

## 13. Resultado verificado (Moto G6 Plus, Android 9, modo offline)

### O que passou

| prova | evidência |
|---|---|
| Modelo extrai de assets | `modelo instalado em 4532 ms`, 51 MB em `filesDir` |
| Vosk carrega | `modelo carregado de vosk-model-small-pt-0.3` |
| Escuta offline ativa | `escutando offline (16000 Hz)` |
| **Sem permissão de rede** | `aapt dump permissions` não lista `INTERNET` |
| APK | **50,1 MB** (era 70,6 MB sem `abiFilters`) |
| Crash | 0 em toda a bateria de testes |
| Léxico de verbos | `lexico carregado: 563 formas verbais` |

### Roteador com léxico real (corpus pt-BR)

```
abrir prumo           -> Abrindo Prumo Entregador.
abriu o chrome        -> Abrindo Chrome.          <- conjugação
executa calculadora   -> Abrindo Calculadora.
lanca youtube         -> Abrindo YouTube.
digita teste          -> Nenhum campo de texto na tela.
voltar                -> Voltando.
home                  -> Inicio.
```

### Transcrição offline (auto-teste TTS -> WAV -> Vosk)

| frase falada | vocabulário livre | gramática de apps |
|---|---|---|
| `argos abrir calculadora` | ✅ exato | ✅ exato |
| `argos abrir o prumo` | ❌ `argos abrir para um` | ✅ **exato** |
| `argos abrir chrome` | ❌ `argos abrir mim` | ⚠️ recupera com variantes |
| `argos digita teste` | ✅ exato | ❌ `argos` |
| `argos voltar` | ✅ exato | ❌ `argos` |

**Conclusão:** vocabulário livre erra nome próprio; gramática restrita acerta nome
próprio mas destrói o resto. Por isso nenhum dos dois é usado sozinho — a escuta é
livre, e a gramática entra só como **recuperação** quando o app não é encontrado.

### Descobertas que mudaram o desenho

1. **Endpointer do Vosk precisa de silêncio de cauda.** No mic real existe; no arquivo
   não. Sem 1 s de silêncio a última palavra era cortada. Artefato do auto-teste.
2. **O léxico do modelo é fechado.** `chrome` -> `mim`, `prumo` -> `para um`.
3. **`[unk]` não salva a gramática.** Com gramática de apps, `digita teste` virou só `argos`.
4. **`EXTRA_PREFER_OFFLINE` em API 28 derruba o reconhecedor do Google na hora.**
5. **O corpus pt-BR não tem anglicismo:** `clicar` tem **0 formas** em 195.751 conjugações.

---

## 14. Como executar

### No aparelho

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.argos android.permission.RECORD_AUDIO
adb shell settings put secure enabled_accessibility_services com.argos/com.argos.entry.ArgosAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Abrir o app, tocar **Iniciar Argos**. Dizer:

> **"Argos, abrir o Prumo"**

### Verificar que está tudo vivo

```
adb shell dumpsys activity services com.argos | grep isForeground
adb logcat -s Argos        # deve mostrar: escutando offline (16000 Hz)
```

### Provar que não há rede

```
aapt dump permissions app-debug.apk | grep -i internet    # tem que sair vazio
```

### Auto-teste sem falar (TTS -> WAV -> Vosk)

```
adb shell am start -n com.argos/.entry.MainActivity --ez selftest true --es phrase "argos abrir o prumo"
adb logcat -s Argos
```

### Rebuild

```
gradlew.bat assembleDebug
```

