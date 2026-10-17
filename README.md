# SpikesBot Kwai • v2.1 beta 🐦

**App Android experimental** para controlar o **jogo original** Don't Touch The Spikes (Ketchapp, pacote `com.ketchapp.donttouchthespikes`) no modo clássico. É um projeto independente, não oficial, sem imagens nem código do jogo original.

## Recursos

- **Sem doces:** tenta escolher o corredor sem espinhos e preservar o pássaro.
- **Com doces:** tenta coletar doces laranja se houver espaço livre no muro seguinte.
- **Cores do pássaro:** rosa, azul, roxo, verde, preto e vermelho.
- **Controle de segurança e frequência:** ajustável na tela inicial.
- **Botão iniciar/parar**, detecção por screenshots e toques do Android AccessibilityService.
- Sem INTERNET, sem anúncios e sem envio automático de capturas. A partir da v1.2, a CAIXA-PRETA guarda imagens temporárias do jogo em armazenamento privado para diagnóstico; o compartilhamento exige ação explícita.
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

## v1.0 — visão com duas paredes e controle de voo

- A mudança não é uma sequência fixa de 2, 4 ou 5 cliques. O algoritmo agora lê **cada quadro** do modo VISÃO AO VIVO e estima a velocidade vertical do pássaro.
- Os limites do cenário são detectados por contraste (teto/chão) e cada parede é analisada com sua **própria cor de fundo**, para identificar triângulos escuros e não confundi-los com a pontuação central.
- É escolhida uma altura-alvo entre as áreas livres da parede de destino, com margem para o corpo inteiro; piso e teto são tratados como obstáculos em todas as decisões.
- O filtro distingue o **corpo verde do pássaro** das estrelas pequenas da animação, evitando acompanhar uma partícula.
- Removida a duplicidade de cooldown de toques que contribuía para o padrão “dois toques e pausa”. O espaçamento é aplicado somente no serviço Android; a visão apenas recomenda subir ou aguardar, conforme a direção da queda.
- O diagnóstico agora mostra a parede analisada, quantidade de linhas de espinhos, posição vertical atual e alvo, estado de subida/queda e se há recomendação de toque.
- O modo CONTÍNUO que funcionava permanece sem modificações.

### Teste
Escolha VISÃO AO VIVO, skin Verde e doces desativados. Autorize a captura de tela inteira, inicie o jogo e observe. Se o pássaro morrer, envie o painel de diagnóstico e preferencialmente um vídeo curto da partida. Essa versão compila, mas não é validação de jogabilidade em tempo real e não garante sobreviver a todos os espinhos.

## v1.1 — trajeto previsto para a parede seguinte

- O sistema não programa grupos fixos de 2/3/5 toques. Ele decide a cada quadro se precisa subir.
- Extrapola brevemente a posição do pássaro pela velocidade observada nos quadros e por uma gravidade aproximada. É uma estimativa, não uma simulação fiel da física do jogo.
- Usa velocidade horizontal e distância até a parede para estimar a altura de chegada; escolhe uma abertura maior que o pássaro na parede da direção atual.
- Mantém teto/chão como limites permanentes, mesmo se a parede estiver momentaneamente sem espinhos.
- Evita novos toques quando o pássaro está subindo rapidamente ou perto do teto. Antecipação de queda (180ms) para não esperar o contato com o chão.
- Tenta preferir trajetos alcançáveis, sem priorizar doces sobre obstáculos.
- Mostra direção, quantidade de linhas perigosas na parede, Y atual, Y alvo e chegada projetada no diagnóstico.
- Excluiu decisões visuais com mais de 280ms na VISÃO AO VIVO para não continuar repetindo uma ação antiga depois de uma transição de tela.
- **Diagnóstico da v1.0 do usuário:** 394 quadros, 251 válidos, 136 sem campo, 7 sem pássaro, 31 gestos concluídos e zero cancelamentos. Quadros perdidos podem incluir a transição quando volta ao próprio aplicativo. A falha principal já não é o envio dos gestos.
- **Validação:** pode-se conferir a identificação das paredes com as quatro capturas fornecidas. Ainda não há vídeo com timestamps para medir a física real ou confirmar melhora de sobrevivência.

### Experimento sugerido
VISÃO AO VIVO, cor Verde, doces OFF e segurança 65%. Faça apenas algumas partidas. Se persistir a queda, envie um vídeo curto para estimar velocidade, impulso do pulo e posição exata das colisões. Não inferir sucesso apenas pela compilação.

## v1.2 — caixa-preta interna, sem conflito entre gravadores

Quando a MediaProjection está ativa, gravar simultaneamente com o gravador de tela do celular pode ser bloqueado. Esta atualização NÃO inicia outra gravação. Ela aproveita o fluxo de quadros que o SpikesBot já recebe com autorização da tela inteira.

- Durante o modo VISÃO AO VIVO, o bot armazena localmente até **70 imagens JPEG recentes** (intervalo de pelo menos 280ms) e um CSV de decisões com tempos, alvo Y, pulo sugerido, visibilidade do campo/pássaro e contador de toques. Não coleta outros apps intencionalmente: só grava quando o serviço de acessibilidade identifica Don't Touch The Spikes em primeiro plano.
- O botão **COMPARTILHAR DIAGNÓSTICO (.ZIP)** gera um pacote pelo Android Share Sheet, usando FileProvider. Ele não é vídeo MP4.
- O compartilhamento só acontece quando a pessoa pede. Antes disso tudo fica no espaço privado do app, e a próxima sessão substitui as imagens da anterior.
- Há aviso na interface de que capturas podem incluir notificações sobrepostas; revise o conteúdo antes de enviar.
- O algoritmo de voo da v1.1 fica preservado para medirmos, em vez de adivinhar novos parâmetros. Os 5 pontos observados ainda não demonstram uma melhora de sobrevivência.

Teste: escolha VISÃO AO VIVO + cor Verde + SEM doces. Jogue até falhar. Pare o bot, toque em COMPARTILHAR DIAGNÓSTICO e envie o ZIP aqui. A análise de sequências de quadros com timestamps permitirá ajustar impulso/gravitidade e detecção de espinhos com mais fundamento.

## v1.3 — física corrigida com o CSV enviado pelo usuário

Os 80 registros temporais, 9 imagens e a caixa-preta mostram que o modo VISÃO ao vivo já consegue enviar gestos contínuos, mas ainda faz previsões ruins de trajetória e às vezes termina a partida após poucos pontos. A coluna `decisao` em diversos registros contém `• doce`, portanto o modo de doces estava interferindo na escolha de alvo.

- **Gravidade aferida com o histórico**: 9 trechos de 3 capturas sem toque adicional indicaram aproximadamente 366–585 pixels/s² na imagem redimensionada, com mediana ~449; a constante anterior era ~1280 pixels/s². Nova aproximação: ~0.98 × altura da imagem por segundo ao quadrado (~475 pixels/s²).
- Corrigida também a estimativa de velocidade horizontal padrão (antes ~147 px/s; o registro mostra cruzamentos frequentemente entre 350–450 px/s), reduzindo grandes extrapolações de queda por tempos de voo irreais.
- Simula duas hipóteses a cada frame (esperar e tocar agora), verifica caminho ao longo da travessia e escolhe o corredor livre da próxima parede que exige menor ajuste em relação à altura prevista.
- Teto e chão geram penalidade alta em ambos os cenários; evita novos pulos quando já sobe rápido ou está muito perto do teto.
- O programa não controla o número de toques por sequência. Há uma única limitação de frequência de envio no AccessibilityService.
- O piloto do modo CONTÍNUO e a caixa-preta são preservados. Modo DOCES permanece opcional; **para calibrar, desligue doces**.
- Diagnóstico informa as duas previsões de chegada e o tempo aproximado até a parede. Não é garantida a sobrevivência: a estimativa do impulso do pulo não está completamente aferida com os dados espaçados.

### Teste específico
No modo VISÃO AO VIVO, escolha cor Verde, SEM DOCES e margem 65%. Teste apenas uma ou duas partidas e depois envie o próximo ZIP da caixa-preta (ou CSV e os quadros próximos da derrota). Não compartilhe outros conteúdos capturados se houver notificações sensíveis.

## v1.4 — corrigir a inatividade medida com a caixa-preta

A caixa-preta enviada contém 64 observações, 32 com campo e pássaro detectados, mas **apenas duas decisões de subir**. O jogo chegou a apenas **2 pontos** numa das partidas registradas.

- **Erro real do código anterior:** as duas alternativas (tocar / esperar) escolhiam independentemente seu melhor corredor. Como a alternativa de esperar tinha um corredor calculado perto da altura prevista, a pontuação de espera quase sempre vencia.
- **Medição corrigida:** os trechos sem novo toque têm velocidade horizontal de aproximadamente 96 a 159 pixels/s no campo reduzido de 216 pixels, mediana **135 px/s**. A v1.3 presumiu pelo menos 346px/s. Isso calculava tempo de chegada à parede curto demais. Usa agora 0.43..0.88×largura, com padrão ~135px/s.
- A nova classe pura `FlightLogic.java` escolhe **uma única altura segura** (independente de tocar/esperar) e compara as duas previsões com o mesmo alvo e com teto/chão.
- Pausa o comando quando o pássaro está no **menu inicial**, identificado por ausência de movimento horizontal real; o usuário inicia a partida manualmente.
- A física ainda é aproximada e a detecção por cores pode falhar, mas cada pulo passa a depender da previsão atual, não de uma contagem fixada.
- Os testes automatizados de regressão (Java puro, sem emulador) simulam queda, subida, teto, chão, atraso do toque, tempo de chegada à parede e faixas de espinhos.
- O modo CONTÍNUO permanece intacto; a caixa-preta ainda pode ser exportada após a partida.

Teste VISÃO AO VIVO, pássaro Verde, doces desligados e margem 65%. Envie o ZIP da nova partida e compare os comandos SUBIR vs AGUARDAR. Não declare a precisão perfeita apenas por compilar.

## v1.5 — corrigir alvo na borda da passagem

A caixa-preta da v1.4 teve **89 registros** com 15 toques distribuídos em três partidas que chegaram a 2, 2 e 3 pontos. O detector enxergava os espinhos, mas a altura real na chegada não coincidiu com a prevista: uma parede tinha triângulos nas linhas 197–206, 223–233 e 276–286. O alvo anterior 249 estava quase encostado na área perigosa.

- Agora o planejador divide os intervalos seguros em faixas contínuas e escolhe **o meio de cada passagem**, com margem adicional de 17–21 pixels para o corpo do pássaro.
- Simula o custo de esperar, tocar imediatamente ou esperar de 80 a 480ms antes de tocar; a cada quadro refaz a decisão. Não existe programação de quantidade fixa de toques.
- Verifica altura **ao longo** da trajetória até a parede, não apenas no final, e penaliza colisões nas paredes, no teto e no chão.
- Protege a reação ao toque anterior por pelo menos 285ms, pois o quadro seguinte pode chegar antes de a nova subida aparecer.
- Para **não gravar outras telas por acidente**, a caixa-preta só armazena JPEGs quando campo e pássaro foram reconhecidos. O compartilhamento do ZIP continua opcional e manual.
- Os testes automáticos verificam casos de teto, chão, subida rápida, toque recente, velocidade medida e a geometria das faixas de espinhos registradas na v1.4.
- O modo CONTÍNUO foi preservado. Ainda não há comprovação de sobrevivência longa; se falhar, compare o diagnóstico com partidas reais.

## v1.8 Recuperação — restauração funcional

Esta versão retorna exatamente ao **núcleo de controle da v1.5**, a última versão que o usuário confirmou jogando até 14 pontos.

- `VisionEngine.java`, `FlightLogic.java`, `SpikesService.java` e `FlightRecorder.java` foram restaurados a partir do commit Git `d0817d6cfd777ec6d1dd810ae7913bd3abfac9cf`. O modo de captura contínua `LiveCaptureService.java` já estava idêntico ao da v1.5.
- Remove o detector automático de overlay vermelho de derrota e o encerramento do serviço de captura incluídos na v1.7, que podem ter contribuído para a parada completa relatada.
- Remove a instrumentação extra de gestos da v1.7. A caixa-preta original com capturas e decisões continua funcionando.
- Não altera o applicationId nem o formato de preferências, para permitir atualizar o APK mantendo as configurações.
- Os testes Java da v1.5 são executados novamente pelo GitHub Actions.

**Importante:** essa reversão é para recuperar o funcionamento, não para aumentar o recorde. Nenhum resultado de partida real foi confirmado para esta compilação.

### Se o bot não tocar depois da atualização
1. Abra SpikesBot, confira **VISÃO AO VIVO** e **Verde**, e toque em **PARAR O BOT** para limpar uma sessão antiga.
2. Desative e reative o serviço de acessibilidade nas configurações do Android, somente se necessário e se confiar no APK.
3. Volte ao app e toque **INICIAR BOT AUTOMÁTICO**; conceda nova permissão de captura de tela inteira.
4. Inicie a partida no próprio jogo manualmente. A visão espera detectar movimento horizontal antes de enviar comandos.
5. Se persistir, informe se ocorre fechamento forçado, falta de toques ou tela congelada; consulte STATUS / DIAGNÓSTICO.

Para melhorar a jogabilidade a partir daqui, uma próxima versão deverá incorporar testes de replay com a caixa-preta real; não confundir uma compilação bem-sucedida com piloto validado.

## v1.9 — capturas e telemetria sem travar o controle

Na caixa-preta v1.8: 64 linhas amostradas e 22 toques contados, com várias passagens de espera e um pássaro perto do fundo da arena. Os JPGs eram comprimidos e gravados na mesma thread que analisa as imagens, podendo reduzir a frequência de decisão.

- Move JPEG e CSV para uma thread de IO separada da MediaProjection; só copia pixels de vez em quando.
- Registra decisões a cada ~120ms e imagens espaçadas a cada ~420ms, até 70 arquivos.
- gestos.csv registra ENVIADO, CONCLUIDO e CANCELADO com o horário real relativo à sessão.
- Atualizações de interface no máximo a cada 430ms para reduzir cargas desnecessárias.
- Preserva integralmente a física da v1.5 e o modo CONTÍNUO: **não promete pontuação maior sem teste real**.
- Exporte a caixa-preta depois de parar para comparar o controle visual com os gestos reais.

## v2.0 — busca de sobrevivência longa, sem prometer infinito

A caixa-preta v1.9 registrou **113 decisões**, **22 gestos enviados e concluídos**, uma partida até 9 pontos e outra que terminou rapidamente. O ponto mais claro é a queda final: Y=216 (33.394s), Y=244 (33.526s, decisão AGUARDAR), Y=314 (33.700s, só então SUBIR). Isso mostra um atraso do controlador de altitude, não um erro do sistema de toques.

- Proteção reativa de queda no `FlightLogic` além da simulação de trajetória futura: considera velocidade vertical, posição prevista em 230 ms e distância à parede. Só antecipa toques quando há risco de chegar baixo demais.
- Evita a regressão da v1.6 ao bloquear as correções extras quando o pássaro ainda está subindo ou já está perto do teto.
- Cooldown adaptativo: 245ms para correção normal, 195ms numa queda de emergência; sem rajadas fixas de cliques.
- Retirado limite artificial de captura de tela de 2 minutos do modo VISÃO AO VIVO. A sessão continua apenas enquanto autorizada pelo usuário e ainda pode ser encerrada por PARAR O BOT, pela notificação do Android ou pelo próprio sistema.
- Os quadros não são transmitidos nem salvos automaticamente fora do armazenamento privado do app. Para sessões longas, a caixa-preta retém os últimos 70 JPEGs; recomenda-se limitar o tamanho dos CSVs em atualização futura.
- Cinco novos testes de regressão cobrem trechos da caixa-preta v1.9, proteção do teto e cooldown após toque.

**Limitação:** não existe garantia de pontuação ou sobrevivência infinitas num jogo com obstáculos variáveis e latência Android. A primeira meta é ultrapassar consistentemente os 14 pontos observados na v1.5, depois 30, 50, 100 e assim por diante. Modo DOCES pode continuar OFF até estabilidade.

Para comparar: VISÃO AO VIVO / Verde / SEM doces / segurança 65%. Faça algumas partidas e compartilhe a caixa-preta se o piloto perder. Verifique as imagens do ZIP antes de enviá-lo.

## v2.1 — planejador de múltiplos pulos, baseado em dados

Na caixa-preta v2.0 fornecida pelo usuário, a última partida mostrava **9 pontos**. O diagnóstico registrou **27 gestos enviados e concluídos, nenhum cancelado**, e em dois casos próximos do fim os callbacks chegaram após 267 e 439ms. A visão estava detectando o pássaro; o planejamento de **apenas um pulo futuro** não era suficiente para alcançar um corredor distante.

- O novo `FlightLogic` simula trajetórias que contêm de 0 a 6 pulos, com tempos iniciais e espaçamentos diferentes. Checa todo o percurso para detectar aproximação perigosa do teto e do chão e verifica a altura ao chegar à parede seguinte.
- O app só **executa o primeiro pulo** do melhor plano, se for apropriado agora, e refaz a simulação a cada quadro novo. Não segue sequências fixas nem age fora do jogo.
- Mantém margem de segurança do corpo do pássaro, cooldown para gestos recentes e estimativa de cerca de 55ms para execução (mas há variação real do Android).
- Caso real de regressão: aos ~35.1s, x=186, y=308, o pássaro precisava alcançar uma passagem alta da parede esquerda. O teste usa as bandas de espinhos realmente vistas em `quadro_0000035098.jpg` e exige que o planejador comece a subir antes da chegada.
- O registro atual é insuficiente para afirmar uma taxa de acerto real; o teste automatizado verifica decisões de situações conhecidas, não uma partida simulada fiel.
- Preserva a captura contínua, caixa-preta e o modo CONTÍNUO. Sem promessa de sobrevivência infinita.

### Teste real
VISÃO AO VIVO, Verde, doces desligados, margem 65%. Teste três partidas e compartilhe a caixa-preta se ocorrer colisão. Compare a pontuação e o comportamento com os 9 pontos do registro v2.0 e com o melhor observado de 14 da v1.5.
