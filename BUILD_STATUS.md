# Build de depuração — Bettdow AC1041-X7

Validação na nuvem em 20/10/2026.

## Resultado

**APK gerado com sucesso:** `app/build/outputs/apk/debug/app-debug.apk` (aproximadamente 2,3 MB).

- Pacote: `com.example.projectorlauncher`, versão `0.1.0`.
- API mínima 28 (Android 9), target/compile SDK 35.
- `:app:assembleDebug :app:lintDebug`: **BUILD SUCCESSFUL**, código de saída 0.
- Assinatura de desenvolvimento validada com `apksigner verify`.
- Metadados do APK conferidos com `aapt dump badging`; não há permissões solicitadas.
- SHA-256: `e2061958a0b0069142873493c4f4c0d1dcfae104ac3f31f7f064649100db75c4`.

O Android Lint concluiu com **0 erros e 5 avisos**: inflação sem parent, orientação landscape fixa, regras de backup em Android 12+, possível overdraw e recurso de texto não utilizado. Relatórios completos em `app/build/reports/lint-results-debug.{html,xml,txt}`. Não foram desativadas verificações ou adicionados baselines para esconder resultados.

## SDK e ambiente

Instalados em `/workspace/android-sdk`:

- Android SDK Command-line Tools (latest), obtidas do repositório oficial Google e verificadas pelo checksum publicado no índice oficial.
- Android SDK Platform 35.
- Android SDK Build-Tools 34.0.0.
- Android SDK Platform-Tools.

Licenças disponíveis aceitas pelo `sdkmanager --licenses`; instalação terminou com código 0. JDK 21, Gradle 8.9, AGP 8.7.3 e Kotlin 2.0.21 utilizados.

A execução usou `ANDROID_HOME=/workspace/android-sdk`, `ANDROID_USER_HOME=/workspace/android-user-home` e `GRADLE_USER_HOME=/workspace/.gradle`. O Gradle foi executado pela distribuição oficial com SHA-256 verificado e propriedades JVM de proxy derivadas do proxy já fornecido pelo ambiente. Nenhuma credencial ou endereço privado de proxy foi gravado no repositório; TLS permaneceu ativo. A compilação anterior, que falhava por ausência de SDK, está resolvida.

Permanecem avisos não fatais de analytics em diretório somente leitura e diferença de versões do XML de metadados do SDK. Não impediram compilação ou lint. A tentativa de enumerar dispositivos com ADB nesta nuvem falhou ao criar `/home/agent/.android` (somente leitura); nenhum dispositivo foi acessado e nenhum comando foi enviado ao projetor.

## Limites da validação

Os cinco atalhos usam exclusivamente `Intent`/`startActivity`. Função inexistente e acesso negado têm diálogos distintos. Não há execução de ADB/shell dentro do aplicativo, solicitação de permissões privilegiadas, alteração do launcher padrão ou desativação do ZEASN.

Compilação e análise estática passaram. Abertura das funções por aplicativo comum, aparência/foco no controle remoto e consumo de RAM **ainda precisam ser validados no Bettdow físico**. Os testes ADB relatados pelo usuário não provam autorização para aplicativos comuns. Não foram executados testes instrumentados ou unitários; tarefas de análise de fontes de teste não equivalem a testes funcionais.

Consulte README.md para instalação por ADB no Windows e checklist de validação manual. O APK é de teste, não de produção.
