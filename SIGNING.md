# Assinatura estável de APKs

## O problema das versões anteriores

Cada runner novo do GitHub Actions cria outra chave `debug.keystore`. Mesmo com pacote e versionCode corretos, Android não permite atualizar um APK assinado por um certificado diferente. A chave privada antiga não pode ser recuperada apenas do APK/certificado público.

Na versão 0.5.0, `CI=true` gera **APK unsigned**. A pipeline só distribui o artefato instalável `projector-launcher-debug` após assinar com uma chave persistente fornecida por GitHub Secrets. Se algum secret estiver ausente, a etapa explica o nome faltante e falha: não fabrica outra identidade de assinatura. PRs executam testes/lint/build sem acesso a secrets e publicam somente `projector-launcher-unsigned`, que não é instalável antes de ser assinado.

## Configurar uma vez (Windows)

Se você ainda possui a chave que assinou a instalação atual, reutilize essa chave. Caso contrário, crie uma nova chave protegida para as próximas versões. **Não desinstale ZEASN nem altere o HOME.** Não é necessário modificar o projetor para criar a chave.

No PowerShell, com o JDK no PATH:

```powershell
# O keytool pedirá a senha de forma interativa. Use uma senha forte e guarde-a.
keytool -genkeypair -keystore "$env:USERPROFILE\projector-launcher-signing.p12" -storetype PKCS12 -alias projector-launcher -keyalg RSA -keysize 3072 -validity 10000

# Base64 é uma codificação, NÃO uma proteção. Cole somente no GitHub Secrets.
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\projector-launcher-signing.p12")) | Set-Clipboard
```

Em **GitHub > repositório > Settings > Secrets and variables > Actions**, crie:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do arquivo, em um único secret.
- `ANDROID_KEYSTORE_PASSWORD`: senha do keystore.
- `ANDROID_KEY_ALIAS`: `projector-launcher`, ou o alias real de sua chave existente.
- `ANDROID_KEY_PASSWORD`: senha da chave (no PKCS12, normalmente igual à do keystore).

Depois limpe a área de transferência, mantenha backup cifrado/offline do keystore e senhas e restrinja quem pode alterar workflows. Nenhum desses valores deve ir em chat, README, código, variáveis públicas ou commits. A pipeline decodifica em `$RUNNER_TEMP`, usa senhas por ambiente (sem imprimir valores) e remove o arquivo com `trap` mesmo em caso de falha.

É recomendável criar também a variável não secreta de repositório `ANDROID_SIGNING_CERT_SHA256`, com a impressão SHA-256 do certificado. Veja-a com:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\projector-launcher-signing.p12" -alias projector-launcher
```

A pipeline compara a impressão e recusa uma chave trocada acidentalmente. Não altere a chave entre versões. Ative proteção/revisão da branch e nunca execute código de PR não confiável com acesso a esses secrets. `pull_request_target` não é usado.

## Gerar um APK atualizável localmente

```powershell
$env:CI = 'true'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
Remove-Item Env:CI
# Sem --ks-pass na linha de comando, apksigner pede a senha.
& "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" sign --ks "$env:USERPROFILE\projector-launcher-signing.p12" --ks-key-alias projector-launcher --out app\build\outputs\apk\debug\app-debug.apk app\build\outputs\apk\debug\app-debug-unsigned.apk
& "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\debug\app-debug.apk
```

Builds locais comuns sem `CI=true` continuam usando a chave debug local do Android SDK, útil para desenvolvimento, mas só são atualizáveis entre si se usarem essa mesma chave. APKs desta branch não podem atualizar instalações anteriores cujo certificado privado foi perdido; não há correção legítima que ignore a verificação do Android. Exporte suas preferências se necessário antes de planejar uma reinstalação apenas do nosso app; esta versão não oferece exportação automática nem executa desinstalações.

## Situação nesta tarefa

A configuração de assinatura está pronta e será testada localmente com uma chave de desenvolvimento. A API GitHub desta sessão retorna Forbidden; não foi possível conferir ou criar GitHub Secrets. Até os quatro secrets serem configurados, um push/manual não disponibiliza APK assinado. O build de PR unsigned e o APK debug local são verificações distintas de uma publicação assinada na pipeline.
