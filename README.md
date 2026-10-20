# Projetor Home

Launcher Android nativo em Kotlin e layouts XML. Projeto inicial, criado do zero; Android 9 (API 28) ou superior. Não foi validado em projetor físico.

## Interface e controles

Layout híbrido com destaque superior inspirado em interfaces de TV, faixa horizontal de atalhos e grade responsiva de aplicativos. Recursos vetoriais locais, relógio e aparência persistente: sistema, claro, escuro e alto contraste independente. Setas do controle remoto movem o foco; centro/Enter ativa cartões. O foco tem borda amarela e mudança de cor. Toque também funciona. Voltar fecha diálogos ou segue o comportamento normal do Android; Home é controlado pelo sistema.

Lista aplicativos de TV e convencionais, priorizando entradas de TV e removendo duplicatas por pacote. Atualiza a lista ao retornar à atividade. Atalhos abrem telas nativas de Configurações, Wi-Fi, Bluetooth, Aplicativos e Tela. Intent indisponível ou bloqueada pelo fabricante mostra um diálogo de erro, sem encerrar o launcher.

### Atalhos do Bettdow AC1041-X7

| Cartão | Intent Android |
| --- | --- |
| Fire Stick / HDMI | Componente `com.hisilicon.tvui/com.hisilicon.tvui.MainActivity` |
| Correção trapezoidal | Ação `com.kystar.settings.SCREEN_CORRECT`, pacote `com.kystar.projector.settings` |
| Zoom | Ação `com.kystar.settings.SCREEN_ZOOM`, mesmo pacote |
| Seleção de fontes | Ação `com.kystar.settings.SOURCE_LIST`, mesmo pacote |
| Orientação da projeção | Ação `com.kystar.settings.PROJECTION_INSTALL_MODE`, mesmo pacote |

São Intents diretas, sem executar ADB, shell ou solicitar permissões privilegiadas. O atalho HDMI abre a atividade informada pelo usuário; seleção/comportamento de entrada fica sob controle do firmware. Os comandos equivalentes foram testados pelo usuário via ADB, mas isso não confirma acesso por um app comum. `ActivityNotFoundException` mostra diálogo de função ausente; `SecurityException` informa bloqueio pelo firmware e sugere usar as configurações ou launcher original. O diálogo permanece até ser fechado com OK/Voltar, acessível pelo controle remoto. Não há tentativa de contornar permissões.

Teste os cinco cartões no aparelho instalado como app comum. Se o firmware interceptar silenciosamente uma Intent, só a observação no dispositivo confirma a abertura; não é possível inferir sucesso físico apenas de `startActivity` retornar sem exceção.

## Segurança do launcher original

O app é instalável e pode ser aberto como aplicativo comum, pelo ícone Projetor Home. Declara HOME apenas para permitir escolha manual pelo usuário quando o Android disponibilizar essa opção. Não altera preferências de HOME, não desativa/remove pacotes, não usa root, administrador de dispositivo, acessibilidade ou permissões privilegiadas. Não há comandos para substituir o launcher original. A instalação não seleciona este app como launcher padrão automaticamente.

## Memória

Pensado para aparelhos com cerca de 1 GB de RAM: Activity nativa sem Compose, AndroidX, bibliotecas de imagens, rede, vídeos, bitmaps de wallpaper, cache de ícones ou serviços permanentes. A descoberta dos aplicativos roda fora da thread principal; os cartões usam texto. A grade mantém uma View por aplicativo e deve ser medida em aparelhos com muitos apps. Não há promessa de consumo medido: uso de memória, fluidez e comportamento de controle remoto ainda precisam ser testados no hardware. O limite de heap do Gradle é da máquina de compilação, não do projetor.

## Compilar no Windows

1. Instale Android Studio e use seu JDK embutido (JDK 17 ou 21). Em Settings > Build, Execution, Deployment > Build Tools > Gradle, selecione esse JDK.
2. Pelo SDK Manager, instale **Android SDK Platform 35**, **Android SDK Build-Tools 34.0.0**, **Android SDK Platform-Tools** e **Android SDK Command-line Tools (latest)**. Compile SDK 35 permite executar a partir da API mínima 28; não exige Android 15 no projetor.
3. Abra esta pasta no Android Studio, aceite as licenças do SDK e sincronize o Gradle. O Studio cria `local.properties` com `sdk.dir`. Não versione esse arquivo.
4. No PowerShell, na raiz do projeto:

```powershell
# Ajuste se o Android Studio/SDK estiver instalado em outro caminho.
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`, assinado automaticamente com chave de desenvolvimento. Não é uma versão de produção.

Para instalar por USB com depuração autorizada:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.example.projectorlauncher/.MainActivity
```

Esses comandos apenas instalam/abrem o aplicativo. Não alteram o launcher padrão. Para remover apenas este app: `adb uninstall com.example.projectorlauncher`.

## Linux / nuvem

Com JDK 17/21, SDK e licenças configurados:

```sh
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_USER_HOME=/workspace/android-user-home
export GRADLE_USER_HOME=/workspace/.gradle
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Gradle 8.9, Android Gradle Plugin 8.7.3 e Kotlin 2.0.21 estão fixados. O wrapper e a distribuição usam checksum oficial. A primeira execução requer acesso a services.gradle.org, downloads de distribuição do Gradle, Google Maven e Maven Central. Proxies podem exigir configuração JVM/Gradle local; não coloque credenciais no repositório nem desative TLS.

## Validação manual necessária

- Emulador Android TV API 28 e aparelho real: abrir pelo ícone, navegar por todas as linhas com D-pad e ativar com centro/Enter.
- Alternar claro/escuro/sistema, ligar alto contraste e reabrir o app para verificar persistência.
- Abrir um app convencional e um de TV, voltar e verificar atualização da lista.
- Conferir atalhos disponíveis e mensagem de indisponibilidade nos demais.
- Confirmar que o launcher original e a configuração de HOME permanecem intactos.
- Medir RAM e renderização com Android Studio Profiler/`adb shell dumpsys meminfo com.example.projectorlauncher`.

O relatório do build e das verificações na nuvem está em `BUILD_STATUS.md`. Não há testes instrumentados nem confirmação de execução no aparelho nesta versão inicial.
