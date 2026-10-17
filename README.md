# SpikesBot Kwai • v0.9 beta 🐦

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

## v0.4 beta — solução para apenas um toque

- **Diferença entre teste e automático:** o botão TESTAR 1 TOQUE sempre enviará apenas um gesto. O botão INICIAR BOT AUTOMÁTICO usa o modo selecionado.
- **Novo modo CONTÍNUO:** envia toques repetidos com intervalos de 250–850ms (padrão 420ms) sem depender da visão do campo. Começa 4s após o início, somente enquanto o jogo original estiver em primeiro plano e encerra a sessão após 60s. É um teste de automatização, **não consegue desviar de espinhos**, e pode interagir com menus enquanto o jogo permanecer em primeiro plano. Acompanhe o teste.
- **Modo VISÃO** ainda é experimental e exige campo/pássaro reconhecidos. Corrigida a avaliação das linhas de espinhos, que deixava linhas intercaladas vazias.
- O START limpa um teste de toque pendente; o contador e o status exibem ações da sessão.
- Não autorize acessibilidade de aplicativos de fontes desconhecidas. Este projeto não inclui bibliotecas externas de automação, dados remotos nem anúncios.

### Teste sugerido
1. Selecione **CONTÍNUO** e intervalo **420ms**.
2. Pressione **INICIAR BOT AUTOMÁTICO**, abra/inicie manualmente a partida.
3. Os toques começam após cerca de 4 segundos e prosseguem durante até 60 segundos, somente no jogo.
4. Use **PARAR O BOT** ao terminar. Se o passarinho morrer, altere o intervalo ou mude para VISÃO e compartilhe capturas/status para calibrar, sem esperar acertos perfeitos.

## v0.5 — calibração inicial com capturas reais de jogo

- Quatro capturas reais do jogo (686×1536) mostraram um pássaro **verde** perto das bordas e espinhos triangulares **estreitos** nas paredes.
- O detector anterior procurava paredes escuras numa faixa muito larga (3–20% e 80–97%). Agora procura triângulos nas bordas (1–9% e 91–99%) com limiar adaptativo relativo ao corredor.
- A busca do pássaro foi ampliada de 18–82% para **2–98% da largura** e tem tentativa de reconhecimento do verde caso a cor escolhida não encontre pássaro.
- A análise de obstáculos limita o campo vertical até ~70% da altura para evitar que a sobreposição de edição/captura do Android, vista nos screenshots, vire um espinho falso.
- Intervalo inicial entre capturas da visão reduzido de 260 para 230ms (sem prometer precisão em partidas rápidas). **CONTÍNUO permanece inalterado**.
- **Validação:** os quatro screenshots foram analisados offline quanto à presença de áreas verdes do pássaro e contornos laterais escuros. Isso não confirma previsão do movimento nem sobrevivência no jogo real.

### Teste em aparelho
1. Em **Tipo de controle**, escolha **VISÃO** (não CONTÍNUO).
2. Em **Cor atual do pássaro**, escolha **Verde**.
3. Use **INICIAR BOT AUTOMÁTICO** e inicie uma partida.
4. Depois de parar, veja **Capturas / campo / pássaro / toques** no status. Se não funcionar, envie esses números e um vídeo curto da partida.
5. Para pausar completamente, pare o bot e desative a permissão de acessibilidade quando terminar.

## v0.6 — corrigir o pulo único em VISÃO

- **Causa encontrada:** a função de custo escolhia o alvo colado à posição atual do pássaro (`abs(y - bird.y)*.88`). Ao chegar ao alvo, o controlador deixava de solicitar novos pulos mesmo enquanto o pássaro caía.
- Corrigido para um alvo estável próximo a 46% da tela, limitado aos corredores livres de espinhos; a coleta de doces continua opcional e subordinada à segurança.
- O modo VISÃO agora calcula a recomendação em cada captura, **mas o serviço envia toques em cadência controlada entre capturas válidas** (aproximadamente 285–405ms, conforme a frequência). Isso evita depender do evento único de uma imagem.
- Se o campo ou o pássaro deixar de ser reconhecido, a autorização para novos pulos é revogada imediatamente. Se as capturas travarem, a recomendação expira em **550ms**. Não há autoclicker infinito.
- Status local mostra captura, campo, pássaro, comando SUBIR/AGUARDAR, posição Y e contador de toques. O modo CONTÍNUO não foi alterado.
- Testes offline nos quatro screenshots já enviados: verde detectável nos quatro; alvo central livre calculado aproximadamente em y=223 numa imagem reduzida a 216×483. Esses testes estáticos não validam acertos em tempo real.
- **Ainda experimental:** a física exata do voo não está modelada. Se o pássaro continuar morrendo, será preciso um vídeo curto da partida e os diagnósticos do status.


## v0.7 — diagnóstico e controle de altitude

- O piloto VISÃO não envia mais pulos apenas porque o pássaro está perto da altura desejada: aguarda início de queda ou aproximação do ponto seguro. Duas batidas seguidas enquanto ainda estava subindo podiam fazê-lo encostar nos espinhos superiores.
- **Diagnóstico persistente:** toques, gestos concluídos, cancelados, capturas válidas, pássaro/campo perdido, erros de captura, capturas travadas e motivo da última decisão; visível quando se volta ao app.
- **Watchdog de captura:** se o callback não retornar em 1,7 s, tenta retomar a leitura em vez de permanecer bloqueado. Uma captura válida recente autoriza gestos apenas por até 780ms; não há modo contínuo oculto.
- O modo CONTÍNUO que já funcionava permanece com a mesma lógica e intervalo.
- A captura no modo VISÃO passa a aguardar um pouco mais entre quadros para evitar limites de frequência do Android.
- **Limitação:** não existe vídeo de gameplay com logs sincronizados, portanto não foi comprovado que a causa dos 2 pulos é queda, perda de visão ou cancelamento de capturas. A tela de diagnóstico ajudará a identificar.

**Teste:** selecione VISÃO, Verde, sem doces e taxa 65%. Inicie uma partida. Ao observar a parada, volte ao app e envie a seção "STATUS / DIAGNÓSTICO" inteira. Não mantenha dois auto clickers ativados ao mesmo tempo.

## v0.8 — corrigir causa dos 2 toques, não aumentar a contagem

Diagnóstico real do usuário na v0.7: **37 capturas, 10 válidas, 27 perdas de campo e 18 erros de captura**, incluindo `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT (3)`. Todos os 14 gestos contabilizados foram concluídos. Isso mostra que as limitações são capturas e percepção, não uma trava em 2 ou 3 toques.

- Nova confirmação do campo pela diferença de brilho no **teto e no chão**, amostrando x=75% para ignorar o placar central e a sobreposição de captura à esquerda. Isso permite reconhecer a arena durante transições dos espinhos laterais, ainda exigindo encontrar o pássaro.
- Intervalo inicial de captura aumentado para 700ms e **backoff adaptativo**: quando o Android retornar o erro 3, aumenta 280ms (máximo 1900ms). Após 10 capturas aceitas, reduz cautelosamente 40ms até o mínimo de 700ms.
- A autorização para pular expira rapidamente entre capturas (máximo 1350ms). **Não há taps cegos ilimitados**, nem controle de menus. O modo CONTÍNUO foi mantido como estava.
- O diagnóstico exibe o intervalo atual de captura, a razão da última decisão, falhas do sistema e número de quadros válidos.
- As 4 imagens reais anteriores, de 686 × 1536 pixels, mostram contraste teto/arena e chão/arena de pelo menos 100 níveis na escala 0–255 à direita. Isso é evidência da nova regra, **não valida o voo em tempo real**.
- A física e a previsão do movimento ainda precisam ser testadas no aparelho. O modo de doces continua opcional e não deve ser priorizado antes de uma partida sobreviver no modo sem doces.

### Teste real sugerido
Use VISÃO, Verde, sem doces, segurança 65% e frequência 65%. Faça uma tentativa e envie apenas o painel de diagnóstico. Compare **perdas de campo** e **falhas de captura** com os números da v0.7. Se continuarem altas, o próximo passo correto é um fluxo de captura de vídeo via MediaProjection, com consentimento, em vez de aumentar a quantidade arbitrária de toques.

## v0.9 — Captura contínua com autorização por sessão

O relatório da v0.8 tinha 164 capturas, 70 válidas, 70 perdas do campo, 24 perdas do pássaro, e nenhuma falha na captura. O uso de `AccessibilityService.takeScreenshot()` com ~700ms entre imagens era insuficiente para o ritmo do jogo.

- O modo VISÃO (posição 2 na interface) usa **MediaProjection** e **ImageReader**, com aproximadamente 10 quadros por segundo, autorização expressa para cada sessão, serviço em primeiro plano e notificação do Android.
- A opção de teste CONTÍNUO segue sem alterações; a visão por screenshots foi mantida como VISÃO ANTIGA.
- O Android pede confirmação para capturar a **tela inteira**; não se deve autorizar gravação de um aplicativo isolado, pois o jogo é aberto depois.
- Os quadros são analisados em memória, somente quando Don't Touch The Spikes está em primeiro plano. Não são gravados nem enviados à internet. O aplicativo continua sem permissão INTERNET.
- O Android mostra a atividade de captura na notificação; **PARAR O BOT** encerra a projeção. É necessário pedir permissão novamente para outra sessão.
- O piloto visual ainda é **experimental**. Capturas mais frequentes eliminam uma limitação conhecida, mas não garantem trajetórias seguras: é necessário calibrar física, detecção de espinhos e condições de fim de jogo.

### Teste sugerido
1. Abra SpikesBot, selecione VISÃO AO VIVO, cor Verde, sem doces.
2. Clique iniciar; autorize o compartilhamento da tela inteira.
3. Inicie a partida manualmente; observe alguns segundos.
4. Volte ao SpikesBot, toque PARAR O BOT e envie o relatório. **Não precisa deixar o compartilhamento rodando** após o teste.
