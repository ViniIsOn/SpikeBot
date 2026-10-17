# SpikesBot Kwai • v0.3 beta 🐦

**App Android experimental** para controlar o **jogo original** Don't Touch The Spikes (Ketchapp, pacote `com.ketchapp.donttouchthespikes`) no modo clássico. É um projeto independente, não oficial, sem imagens nem código do jogo original.

## Recursos

- **Sem doces:** tenta escolher o corredor sem espinhos e preservar o pássaro.
- **Com doces:** tenta coletar doces laranja se houver espaço livre no muro seguinte.
- **Cores do pássaro:** rosa, azul, roxo, verde, preto e vermelho.
- **Controle de segurança e frequência:** ajustável na tela inicial.
- **Botão iniciar/parar**, detecção por screenshots e toques do Android AccessibilityService.
- Sem INTERNET, sem anúncios, sem envio de capturas, sem salvamento de imagens e sem permissão para acessar outros aplicativos ou contas.
- Planejado para uso junto do **gravador de tela nativo do Android**, em vídeos verticais para o Kwai.

## ⚠️ Limitações que você precisa conhecer

A IA de visão desta versão é uma **heurística de detecção de cor e espinhos**, não uma IA treinada. **Não está calibrada com gameplay real do seu celular e não garante sequer uma partida sem erros.** O jogo pode ter mudado suas cores, fundos e física. O serviço de acessibilidade pode ser lento para uma partida rápida. Não funciona nos modos Cave, Park ou Duel. Em alguns dispositivos, capturas de tela de outros aplicativos são bloqueadas.

A v0.2 apenas tenta jogar quando identifica um pássaro E espinhos laterais. Se não os reconhecer, **não toca na tela**. Ela não inicia automaticamente novas partidas, não toca em anúncios e não garante recordes.

## Como gerar o APK (GitHub Actions)

1. Crie um repositório **vazio**, como `SpikesBot-Kwai`, na sua conta GitHub.
2. Coloque **todos os arquivos e pastas deste projeto** na raiz do repositório, preservando `.github/workflows/android-apk.yml`.
3. Após enviar para a branch `main` ou `master`, abra **Actions → APK Android - SpikesBot → execução mais recente**.
4. Espere a compilação terminar. Em **Artifacts**, baixe `SpikesBot-Kwai-v0.2-beta-APK`.
5. Extraia o ZIP do artefato. O APK compilado se chama `app-debug.apk`.

> É um APK de depuração do seu próprio projeto. Não é um APK oficial da Ketchapp. Não foi compilado ou testado em um aparelho neste ambiente; a ação do GitHub faz a compilação real.

Outra opção: instale Android Studio, abra o projeto, configure JDK 17 e SDK 35, e execute `gradle :app:assembleDebug` com Gradle 8.9.

## Como usar

1. Instale o jogo original da Ketchapp pelo Google Play.
2. Instale seu APK compilado e abra **SpikesBot Kwai**.
3. Abra **Configurar acessibilidade** e ative somente o serviço **SpikesBot - controle de jogo**, se o Android permitir.
4. Escolha a cor do pássaro, o modo **com/sem doces** e seus parâmetros.
5. Toque em **INICIAR NO JOGO**. O app abre o jogo e aguarda uma partida clássica em andamento. **Inicie a partida manualmente**.
6. Volte ao app e toque em **PARAR O BOT** ao terminar, ou desative seu serviço de acessibilidade.
7. Para gravar no Kwai, use o gravador de tela do próprio Android e informe que a jogabilidade é automatizada.

Se o Android avisar que não é seguro conceder permissões a um APK instalado de fora da loja, respeite o aviso e não conceda permissões sem verificar a procedência do aplicativo.

## Privacidade e funcionamento

A captura usa `AccessibilityService.takeScreenshot` (API 30+) e `dispatchGesture` (API 24+). O bot **só envia gestos quando o pacote do aplicativo em primeiro plano é o jogo permitido**. Não precisa de root, ADB, internet, gravação de áudio nem sobreposição de janelas.

O Android pode restringir o serviço por motivos de segurança ou mudar seu comportamento em versões futuras. Não há mecanismo para contornar essas restrições.

## Arquitetura

- `MainActivity.java` — configurações / iniciar / parar.
- `SpikesService.java` — captura periódica / validação do jogo / gestos de toque.
- `VisionEngine.java` — identificação das cores, corredores, alvo e decisão de pulo.
- `Config.java` — configurações locais.
- `.github/workflows/android-apk.yml` — compilação automática com Gradle.

**Próxima etapa recomendada:** receber capturas reais de duas ou três partidas, inclusive uma com doce, para ajustar o detector de espinhos e os intervalos de pulo antes de afirmar que funciona bem.

## Ícone e configurações restritas (v0.2)

O ícone agora é **vetorial XML**, desenhado com formas e cores, sem geração de imagem nem arquivos externos. O aplicativo também contém um botão **Abrir detalhes do app** e uma explicação sobre o aviso de configuração restrita do Android. Essa restrição é do sistema: o aplicativo não consegue desativá-la por código. Use a opção de autorização do próprio Android somente se confiar no APK.

**Atenção ao atualizar:** o APK debug criado em outro ambiente GitHub Actions pode ter assinatura diferente. Se aparecer `O app não foi instalado`, talvez seja preciso desinstalar a versão anterior antes de instalar esta (isso apaga configurações locais).

## v0.3 beta — correções após teste em Android 14

- **Correção provável do fechamento constante:** as capturas da acessibilidade vêm como `Bitmap.Config.HARDWARE`. A versão anterior redimensionava essa imagem e chamava `getPixels()`; isso pode lançar `IllegalStateException`. A nova versão cria **explicitamente uma cópia ARGB_8888** antes da análise.
- Processamento de imagens em thread de trabalho para reduzir travamentos da interface, e captura limitada pelo Android.
- Tratamento de exceções de captura/detector/toque com diagnóstico local em Configurações do app (sem coleta/transmissão de dados).
- Novo botão **TESTAR 1 TOQUE NO JOGO**: testa somente uma ação no jogo em primeiro plano, sem modo automático. Útil para distinguir um problema de acessibilidade de um problema de detecção visual.
- Fila de análise otimizada para reduzir lixo temporário. Mantidos os modos **com doces** e **sem doces**.
- Por segurança, o botão INICIAR ainda exige que o usuário abra uma partida manualmente: o bot **não clica automaticamente em menus ou anúncios**.

Esta versão tem compilação automatizada, mas ainda precisa de testes em um dispositivo real com o jogo instalado. Não há promessa de acerto perfeito nem garantia de que o jogo atual seja reconhecido visualmente. Se não houver movimento, teste o botão de um único toque e envie um print do campo jogável e os estados de **Capturas / campo / pássaro** exibidos na tela do SpikesBot.
