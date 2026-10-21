# Projetor Home · 0.5.0 — Free My TV Edition

Launcher Kotlin + XML para o Bettdow AC1041-X7, Android 9/API 28 ou superior, com foco em controle remoto e aparelhos com aproximadamente 1 GB de RAM. É uma evolução do projeto existente, com o mesmo pacote `com.example.projectorlauncher`.

## Experiência de TV

A versão 0.5.0 adapta componentes e a composição do [Free My TV](https://github.com/simbasjungles/free-my-tv), sob MIT: recomendações largas/estreitas com legendas externas, abas Explorar/Filmes/Séries, tipografia Inter, dock translúcido inferior, mola de foco e sombra nine-slice própria para GPUs antigas. [FREE_MY_TV_ADAPTATION.md](FREE_MY_TV_ADAPTATION.md) documenta arquivos estudados, copyright, portas, limites e diferenças.

**Aplicativos** abre a grade completa com ícones reais; **Gerenciar favoritos** altera os seis apps da prateleira. OK prolongado abre opções; D-pad e Voltar continuam funcionando. A engrenagem abre nosso dashboard: projeção, áudio, rede, Bluetooth, entradas, apps, aparência e sistema. ZEASN e o HOME padrão não são alterados.

**TMDB:** Configurações > Aparência > TMDB e recomendações. Insira a chave/token apenas no aparelho; a chave é cifrada com Android Keystore, não vai ao GitHub/APK. A janela do app usa modo imersivo local (sem alterar configurações do sistema). Sem credencial, três cartões de arte local original mantêm a composição completa e abrem a grade de apps. Com credencial, picks dos serviços instalados abrem o respectivo aplicativo. A consulta autenticada precisa ser validada com uma chave real.

**APK pela pipeline e updates:** [SIGNING.md](SIGNING.md) explica os quatro GitHub Secrets necessários e o certificado estável. Sem eles, o build compila, mas a pipeline recusa distribuir um novo APK com assinatura temporária. O artefato de PR é unsigned e não é instalável até assinar. Builds locais comuns usam a chave debug local; não prometem compatibilidade com chaves antigas perdidas.

## Aparência, movimento e áudio

Em **Configurações > Aparência**, escolha Claro, Escuro ou Alto contraste. A seleção se aplica aos três espaços principais, submenus, menus de aplicativos, mensagens de erro, foco e textos. As escolhas anteriores de tema claro/alto contraste são migradas; novas instalações usam escuro. Favoritos e preferências são salvos em SharedPreferences.

Sons da interface podem ser ativados/desativados. Navegação e confirmação usam efeitos nativos diferentes (`FX_FOCUS_NAVIGATION_*` e `FX_KEYPRESS_RETURN`), respeitando as opções e o volume do Android. O firmware pode mapear ou silenciar esses efeitos; não há assets de áudio nem controle forçado de volume.

Animações usam somente APIs nativas: mola amortecida adaptada do upstream (1,16× no dock, 1,05× em apps/picks e 1,025× nos demais controles), transição de tela de 160 ms, entrada de menu de 140 ms e saída de 100 ms. **Reduzir animações** desativa escala, fades (inclusive entre wallpapers), deslocamentos e animações de janela dos menus. Não há mascote, loop contínuo de animação ou vídeo. A troca opcional de wallpapers usa crossfade de 380 ms após 180 ms de foco estável.

## Ícones reais e memória

`ActivityInfo.loadIcon(PackageManager)` carrega os ícones das atividades/aplicativos instalados, inclusive adaptativos. O `ImageView` usa FIT_CENTER, sem tint, com proporção e transparência ARGB preservadas. O ícone genérico do Android só é usado quando o recurso original não está disponível. Na versão antiga os cartões eram somente botões de texto; esse foi o motivo de não haver ícones.

A descoberta de apps e a leitura dos ícones usam um único executor fora da thread principal. Imagens são carregadas somente para cartões visíveis, limitadas a 144×144 pixels (até 81 KiB por ícone) e liberadas quando saem da área visível. Não há cache permanente de bitmaps. A grade mantém metadados e uma View por app: aparelhos com muitos apps ainda precisam de perfil de memória. Não são usados Compose, bibliotecas de imagens, assets grandes ou serviços permanentes. A dependência JUnit é exclusiva dos testes e não entra no APK.

## Integrações preservadas do projetor

| Local | Intent |
| --- | --- |
| Entradas > Fire Stick / HDMI | Componente `com.hisilicon.tvui/com.hisilicon.tvui.MainActivity` |
| Imagem > Correção trapezoidal | `com.kystar.settings.SCREEN_CORRECT`, pacote `com.kystar.projector.settings` |
| Imagem > Zoom | `com.kystar.settings.SCREEN_ZOOM`, mesmo pacote |
| Entradas > Seleção de fontes | `com.kystar.settings.SOURCE_LIST`, mesmo pacote |
| Imagem > Orientação da projeção | `com.kystar.settings.PROJECTION_INSTALL_MODE`, mesmo pacote |

Os comandos equivalentes foram verificados pelo usuário via ADB. Os atalhos no app usam exclusivamente Intents e `startActivity`, sem shell/ADB/root. Função ausente e acesso negado geram menus de erro legíveis no tema atual. O sucesso via ADB não garante autorização para apps comuns; se o firmware bloquear, use as configurações ou o launcher original. Não há tentativa de contornar permissões.

## ZEASN e compatibilidade de atualização

Não removemos, desativamos ou configuramos o ZEASN, não alteramos preferências de HOME e não selecionamos este launcher como padrão. A declaração HOME existente permanece para escolha manual quando o usuário desejar; o app também abre pelo ícone comum.

O pacote é o mesmo, versionCode é **5** e versionName é **0.5.0**. Atualização por cima exige também a **mesma chave de assinatura**. Builds debug da nuvem, do seu Windows e de runners diferentes podem usar chaves diferentes. Se o Android reportar assinatura incompatível, não desinstale automaticamente: perderia os dados locais do nosso launcher. Use a chave original para compilar uma atualização compatível. Isso não envolve a assinatura ou a instalação do ZEASN.

## Compilar no Windows

Instale Android Studio, JDK 17 (ou o JDK compatível embutido no Studio), SDK Platform 35, Build-Tools 34.0.0 (padrão do AGP 8.7.3), Platform-Tools e Command-line Tools. Aceite as licenças pelo SDK Manager. O projeto fixa Gradle 8.9, AGP 8.7.3 e Kotlin 2.0.21.

No PowerShell, na raiz extraída do projeto:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`. Ajuste os caminhos conforme sua instalação. O Android Studio também pode criar `local.properties` com `sdk.dir`; não versione esse arquivo. SDK 35 é usado para compilar, mas o app executa a partir da API 28.

Instalação/abertura por USB com depuração autorizada:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.example.projectorlauncher/.MainActivity
```

Isso abre o aplicativo, sem configurar o HOME padrão. Não há comandos que alterem pacotes do projetor.

## GitHub Actions e validação

Em **Actions > Build debug APK**, uma execução manual, PR ou push relevante na `main`/`feat/**` executa testes unitários, lint e build com Java 17, instala pacotes atuais do SDK e assina com a chave protegida e publica `projector-launcher-debug` por 14 dias (exceto PRs, que geram `projector-launcher-unsigned`). O ZIP assinado contém `app-debug.apk`; veja SIGNING.md antes de configurar a pipeline. A primeira compilação requer acesso aos repositórios oficiais Gradle/Google Maven/Maven Central; mantenha TLS e checksums ativos.

Na nuvem preparada:

```sh
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_USER_HOME=/workspace/android-user-home
export GRADLE_USER_HOME=/workspace/.gradle
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

O proxy deste ambiente pode exigir propriedades JVM de proxy locais, sem credenciais no projeto. Resultados locais estão em `BUILD_STATUS.md`. Testes unitários verificam contraste mínimo das paletas e regras dos favoritos; não substituem testes de interface.

No Bettdow físico, conferir:

1. Atualização com a chave correta e restauração das preferências da versão 0.2.
2. D-pad entre todas as telas, rolagem da grade, OK, OK prolongado e Voltar.
3. Ícones reais, favoritos (inclusive lista vazia e limite de seis) e persistência após reabrir.
4. Temas em menus e mensagens, sons ativados/desativados e movimento reduzido.
5. Os cinco atalhos proprietários como app comum, telas de áudio/rede/Bluetooth e estado de Wi-Fi após conexão/desconexão.
6. RAM/fluidez com Android Studio Profiler ou `adb shell dumpsys meminfo com.example.projectorlauncher`, e preservação do ZEASN/HOME original.

## Base preservada da versão 0.4.0 — glass e cinema

Painéis translúcidos com cantos arredondados, brilho estático e bordas em camadas dão unidade à navegação, cartões e menus. Não há blur em tempo real, RenderEffect, sombras grandes ou frameworks novos. Alto contraste mantém superfícies opacas. Testes verificam texto normal/secundário sobre glass com fundos extremos claros/escuros. O cabeçalho usa uma faixa de vidro; os favoritos de Home não repetem a legenda “★ Favorito”.

Na tela inicial, o foco D-pad em favoritos de streaming troca o cenário sem abrir o app. São reconhecidos pacotes de Netflix, Prime Video, Max/HBO, Disney+, Globoplay, YouTube, Apple TV e Paramount. As cenas são paisagens abstratas originais, não imagens de filmes/séries. Consulte ARTWORK.md para procedência e licença CC0 dos desenhos. Apps ausentes/desconhecidos e controles sem app associado usam um cenário neutro.

O carregamento espera 180 ms de foco estável. Pedidos anteriores são invalidados, inclusive resultados de rasterização que terminam atrasados. A transição dura 380 ms; movimentos rápidos não mantêm um cache de todos os serviços. Há apenas o quadro atual e o próximo, em RGB_565, até 1920×1080 (aproximadamente 8 MiB para dois wallpapers 1080p). Em resoluções menores usa o tamanho real; telas maiores usam amostragem limitada a 1080p para proteger a RAM. Imagens são liberadas ao sair da Activity/trocar a tela. Os ícones continuam com seu orçamento separado e carregamento somente na área visível.

Em **Configurações > Aparência**, **Papéis de parede dinâmicos** pode ser desativado para manter somente o cenário neutro. Movimento reduzido troca o cenário sem fade; Alto contraste desativa as imagens e o glass translúcido. As preferências ficam salvas. A base 0.4 não importava fotos. Na edição 0.5, o acesso opcional a fotos TMDB é descrito em FREE_MY_TV_ADAPTATION.md; sem credencial, o fundo permanece local.

No projetor, validar foco rápido entre serviços, passagem por apps sem mapeamento, retorno de apps externos, temas, menus, legibilidade sobre o fundo e memória/fluidez com wallpaper ativado e desativado. O estilo do desenho e a fidelidade das cores precisam de avaliação no hardware; testes locais não medem desempenho no AC1041-X7.
