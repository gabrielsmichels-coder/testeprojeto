# Free My TV Edition — análise e adaptações

Referência estudada: https://github.com/simbasjungles/free-my-tv
Commit upstream: `62e883b3179c8cafb7a2b09960eabfe7eea6ef1c`.

Copyright (c) 2026 Free My TV contributors. Código original MIT. O texto MIT integral está em `app/src/main/res/raw/free_my_tv_license.txt` e é incluído no APK. Inter Variable foi copiado de `app/assets/fonts` do upstream, com sua licença SIL OFL integral em `res/raw/inter_font_license.txt`. Nenhuma foto de filme/série da documentação do upstream foi copiada.

| Referência | Adaptação nesta versão |
| --- | --- |
| `HomeView.java` | `HomeStage.kt` mantém camadas independentes, safe area, título/abas, três recomendações (larga + duas estreitas), legendas fora das imagens e dock inferior ancorado. Views nativas mantêm foco/acessibilidade e só a camada afetada é invalidada; não forçamos hardware layers grandes no Android 9. |
| `Backdrop.java` | `BackdropPipeline.kt` recorta/faz sampling em worker, aplica scrims laterais/inferiores e produz frost 128×72. `adapted/BackdropBlur.java` porta o algoritmo original de redução/box blur, com cuidado para não reciclar o bitmap-fonte quando os tamanhos coincidem. |
| `Scene.java` / `Frost.java` | `LegacyGlass.kt` amostra o frost em coordenadas de tela via BitmapShader filtrado e aplica tint/borda. Não foi portado o código RuntimeShader/AGSL nem o loop de aurora. |
| `Shadows.java` | Portado para `adapted/Shadows.java`, sempre no caminho legacy: blur da pequena máscara alfa uma vez, desenho nine-slice durante a animação; cache reduzido de 64 para 8 máscaras por drawable. |
| `Spring.java` | Portado com a mesma integração por subpassos; `SpringMotion.kt` usa um loop Choreographer compartilhado que para quando todas as molas assentam, cancela ao sair da tela e respeita movimento reduzido. |
| `Recs.java` | `RecommendationRepository.kt` consulta trending/provedores via TMDB somente com credencial do usuário, limita a três picks de apps instalados, região configurável e cache privado limitado. Não embute uma chave no APK. |
| `HomeApps.java` | Preservamos o PackageManager e a prateleira de favoritos existentes. Logos vêm dos apps instalados; não copiamos logos baixados/Wikimedia. A grade completa continua separada. |

## Direção visual e diferenças deliberadas

A captura `.github/preview.webp`, o layout e as constantes do HomeView foram examinados: margem 80 em canvas 1920, recomendação larga de três colunas e duas estreitas, espaço entre cartões, títulos/metadados abaixo, dock com cantos concêntricos e ícone crescendo a partir do centro. A composição aqui usa a área útil nativa, preservando essa hierarquia em resoluções/densidades diferentes. Não é uma cópia pixel a pixel nem uma medição de fidelidade/performance no projetor.

- Mantemos a identidade e os recursos existentes: pacote `com.example.projectorlauncher`, favoritos e SharedPreferences anteriores, temas claro/escuro/alto contraste, grade de apps, Wi-Fi/entradas/relógio, dashboard próprio e cinco Intents do Bettdow.
- O dock usa ícones originais reais (não wordmarks proprietários embutidos). Cresce 16% por mola; os picks crescem 5%. Valores menores que o dock 20% do upstream evitam colisões em seis favoritos e telas menores.
- Sem chave TMDB: três cartões completos de **arte local original**, identificados, sem títulos fictícios anunciados como filmes reais. Abrir um cartão local leva aos aplicativos. Não há scraper ou imagens promocionais no repositório.
- Com TMDB: três picks dos provedores instalados, até oito candidatos consultados, região BR/PT/US. Selecionar abre o aplicativo do serviço, não promete um deep link direto ao título. Abas Explorar/Filmes/Séries consultam os respectivos catálogos.
- O fundo cinematográfico continua reagindo ao foco de apps/picks com debounce de 180 ms e fade de 380 ms; somente dois quadros completos são retidos. Cada quadro tem um frost minúsculo, calculado fora da UI. Sem efeitos contínuos, loops de wallpaper ou RuntimeShader.
- Cache de download: até seis arquivos e 16 MiB, no máximo 2 MiB por imagem; decode temporário limitado a quatro megapixels, thumbnails de até 560×320; fundo de até 1920×1080 RGB_565. Ícones visíveis continuam limitados. Há alocações transitórias de decode/processamento; não afirmamos RAM total ou FPS sem medir.
- Clima, criptomoedas, assistente, remapeamento de HOME, serviço de acessibilidade e ferramentas de instalação do upstream não foram incorporados. Nenhum código que desative pacotes foi copiado.
- Os controles do projetor ficam em Configurações; não há comandos ADB dentro do app. A autorização via shell do fabricante não garante acesso para apps comuns; mensagens de erro continuam presentes.

## TMDB e privacidade

Configurações > Aparência > TMDB e recomendações aceita API Key v3 ou API Read Access Token v4, inserido **no aparelho**, e região. A credencial é cifrada AES-GCM com chave Android Keystore; o formulário impede screenshots e autofill. Não é incluída em BuildConfig, GitHub Actions, logs ou fotos. Remover a credencial volta à arte local. Falhas de rede/chave retornam uma UI completa, sem registrar URLs com credenciais.

Destinos fixos: `api.themoviedb.org` e `image.tmdb.org`, HTTPS com verificação normal, timeouts e respostas/downloads limitados. Nenhuma conexão é necessária sem chave. O catálogo é cacheado em memória por até seis horas enquanto a Activity/repositório existir; não há serviço em segundo plano. A API autenticada ainda precisa de teste com uma chave válida e do atendimento aos termos/atribuição do TMDB antes de distribuição comercial: https://www.themoviedb.org/documentation/api/terms-of-use.

A frase de atribuição TMDB é apresentada no painel. O uso de marca/logo aprovado exigido pelos termos precisa ser conferido pelo distribuidor; esta edição sem credencial não empacota artes/logos do TMDB.

## Assinatura e entrega

Veja SIGNING.md: builds CI são unsigned até uma etapa com chave estável protegida por Secrets; PRs sem secrets geram um artefato unsigned de inspeção. A pipeline nunca volta silenciosamente à chave temporária do runner. Sem configurar os secrets, a publicação instalável fica bloqueada explicitamente.

## Testes físicos pendentes

Não reutilize os números de RAM/FPS do upstream (medidos em outro hardware) como se fossem nossos. No AC1041-X7, verificar tamanho e legibilidade, foco em todos os estados, Back, pressão longa, favoritos antigos, temas, som, movimento reduzido, credencial Keystore, mudança de região, fotos/cache/falhas de rede, retornos de apps e acesso a HDMI/keystone/zoom/fontes/orientação. Medir heap/GPU/frame-time, inclusive após vários retornos e trocas de tela.
