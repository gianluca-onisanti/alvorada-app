# Alvorada

Um despertador para Android que não aceita ser desligado. Ele acorda você, mostra o que
você prometeu fazer ao acordar e continua cobrando até você **provar com uma foto** que
fez. Cumprir vira XP e moedas; moedas viram prêmios que você mesmo cadastrou.

App pessoal, sem loja e sem servidor: tudo mora no aparelho, distribuído por APK direto.

---

## Índice

- [Por que ele existe](#por-que-ele-existe)
- [Um dia usando o app](#um-dia-usando-o-app)
- [As cinco abas](#as-cinco-abas)
- [Instalação e primeiro uso](#instalação-e-primeiro-uso)
- [Configurando o seu dia](#configurando-o-seu-dia)
- [Dois tipos de despertador](#dois-tipos-de-despertador)
- [Desligar: totalmente ou só a próxima](#desligar-totalmente-ou-só-a-próxima)
- [Repetição: quatro modos](#repetição-quatro-modos)
- [Pontos, níveis e sequência](#pontos-níveis-e-sequência)
- [Backup](#backup)
- [Decisões de design](#decisões-de-design)
- [Arquitetura](#arquitetura)
- [Como testar](#como-testar)
- [Quando o alarme não toca](#quando-o-alarme-não-toca)
- [Desenvolvimento](#desenvolvimento)
- [Limitações e próximos passos](#limitações-e-próximos-passos)

---

## Por que ele existe

Todo despertador tem o mesmo defeito: o botão de desligar encerra a transação. Você
acorda, desliga e volta a dormir — ou levanta e não faz nada do que tinha planejado. O
alarme cumpriu o contrato dele, e o seu dia não começou.

O Alvorada troca o contrato. Desligar o despertador não termina nada: **abre uma dívida**.
Cada missão que exige evidência ganha um prazo, e se a foto não chegar dentro dele o
aparelho volta a tocar. Fotografar a cama arrumada é trabalhoso de falsificar às 6h da
manhã — e é justamente esse atrito que faz a diferença entre "desliguei o alarme" e
"comecei o dia".

O resto do app existe para essa mecânica não virar uma fonte de culpa. Por isso XP nunca
decresce, por isso a sequência aceita 70% do dia em vez de exigir perfeição, por isso a
cobrança tem um teto e para de insistir. Um app que pune demais é desinstalado na terceira
semana ruim, e um despertador desinstalado acorda ninguém.

## Um dia usando o app

```mermaid
flowchart TD
    A["06:00 — o despertador toca<br/>tela acende sozinha sobre o bloqueio"] --> B{Missões do dia}
    B --> C["Sem foto: marca ali mesmo<br/>ponto cheio, no horário"]
    B --> D["Com foto: 'Fazer depois'<br/>abre a janela de evidência"]
    D --> E["Prazo correndo<br/>ex.: 30 min"]
    E -->|Foto chega| F["Missão cumprida<br/>cobrança cancelada"]
    E -->|Prazo estoura| G["O aparelho volta a tocar<br/>modo cobrança, sem soneca"]
    G -->|Foto chega| F
    G -->|Teto de cobranças| H["Missão não cumprida<br/>para de insistir"]
    F --> I["XP + moedas no extrato"]
    C --> I
    I --> J["03h — varredura diária<br/>fecha o dia, avalia a sequência"]
```

Em palavras, o mesmo caminho:

1. **O despertador toca.** A tela acende sozinha por cima do bloqueio, com o som no canal
   de alarme — passa por cima do silencioso e do Não Perturbe.
2. **As missões do dia aparecem na própria tela do alarme.** As que não exigem foto você
   marca ali, e elas valem ponto cheio: foram feitas no horário, que é o comportamento que
   o app quer reforçar.
3. **Ao dispensar, as missões com foto viram dívida** com prazo — a janela de evidência,
   configurável por missão (padrão de 30 minutos).
4. **Se a foto não chegar no prazo, o aparelho toca de novo**, em modo cobrança. Cobrança
   não tem soneca: adiar é exatamente o problema que ela existe para resolver.
5. **A cobrança se repete no intervalo configurado** até o teto da missão (padrão: 3). Aí
   ela vira "não cumprida" e o app se cala. Insistir para sempre viraria ansiedade.
6. **Cada missão cumprida credita XP e moedas** num extrato append-only. No prazo, valor
   cheio; depois do prazo, XP cheio e 60% das moedas.
7. **Por volta das 03h a varredura fecha o dia anterior**, avalia a sequência e reafirma
   todos os agendamentos.

## As cinco abas

| Aba            | O que faz                                                                                             |
| -------------- | ----------------------------------------------------------------------------------------------------- |
| **Painel**     | A home. Fração de missões do dia, nível, moedas, sequência, faixa dos últimos 7 dias e o que falta.   |
| **Relógio**    | Seus despertadores, agrupados por categoria. É onde se cria e edita alarme e missão.                  |
| **Evidências** | A dívida fotográfica: o que falta com o prazo correndo, e o que já foi entregue hoje com miniaturas.  |
| **Galeria**    | Todas as fotos, agrupadas por dia, categoria ou despertador.                                          |
| **Prêmios**    | A loja. Recompensas que você cadastra com um preço em moedas, e o histórico de resgates.              |

**Ajustes** não é aba: é a chave no cabeçalho do Painel. É tela de configuração e
diagnóstico, aberta em dias raros — uma aba permanente sugeriria que faz parte da rotina,
ocupando o lugar de algo que faz.

## Instalação e primeiro uso

Pelo Android Studio, com o celular ligado por USB e **Depuração USB** ativa, basta `Run`.
Por linha de comando, veja [Desenvolvimento](#desenvolvimento).

Na primeira abertura o app mostra uma tela de boas-vindas com um único pedido: **passar
pelos Ajustes antes de confiar nele**. Vale a pena obedecer. O despertador depende de
cinco permissões que o Android nega por padrão, e todas falham em silêncio — nada avisa
que o alarme não vai tocar, você descobre na manhã em que ele não toca.

Cada item da tela de Ajustes explica o que quebra sem ele e traz um botão que leva direto
à tela certa do sistema:

| Item                         | O que acontece sem ele                                                          |
| ---------------------------- | -------------------------------------------------------------------------------- |
| **Alarme exato**             | O sistema atrasa o disparo em minutos para poupar bateria. 7h00 deixa de ser 7h00. |
| **Notificações**             | O alarme toca dentro de um serviço em foreground, que exige notificação. Bloqueadas, ele nem começa. |
| **Canal "Despertadores"**    | Silenciado, a tela do alarme deixa de abrir sozinha mesmo com as notificações liberadas. |
| **Tela cheia sobre o bloqueio** | O alarme vira um aviso discreto no topo em vez de tomar a tela.                |
| **Otimização de bateria**    | O sistema mata o app entre um alarme e outro. É a causa número um de despertador que não toca. |

Se o seu aparelho for Xiaomi, Samsung, Huawei, Oppo, Motorola ou Vivo, aparece também um
aviso do fabricante: essas marcas mantêm um matador de processos próprio, acima das
configurações padrão do Android, e o Android não expõe essas telas por intent — é preciso
ir a pé nas configurações do sistema. O [dontkillmyapp.com](https://dontkillmyapp.com) tem
o passo a passo por modelo.

Enquanto algum item estiver pendente, um aviso fica fixo no topo do Painel. Ele não é
dispensável de propósito, e some sozinho quando tudo passa — inclusive se o sistema
revogar uma permissão meses depois, que é o caso que ninguém procura.

Por último, o **teste de fogo**, ainda em Ajustes: agende um alarme para daqui a 1 minuto,
bloqueie a tela e guarde o celular. Se não tocar ali, não vai tocar às 6h.

## Configurando o seu dia

A ordem que funciona:

1. **Crie uma categoria** (pasta) no Relógio — "Manhã", "Academia", "Estudo". Cada uma tem
   cor própria, e é por ela que o Painel agrupa o progresso do dia.
2. **Crie um despertador** dentro dela: horário, dias, som, volume, soneca e teto de
   sonecas. O som pode ser um do sistema ou um arquivo seu — arquivos escolhidos são
   **copiados** para dentro do app, então apagar o original depois não emudece o alarme.
3. **Adicione missões ao despertador.** Cada missão tem título, dias e **quanto vale** —
   veja abaixo. Por padrão ela **segue os dias do despertador**; desmarque a caixa para
   dar dias próprios a ela, sempre um subconjunto dos dias do alarme. Se ela **exigir
   evidência**, configure também a janela do prazo, o intervalo entre cobranças e o teto
   de cobranças.
4. **Cadastre prêmios** na aba Prêmios, com o preço em moedas. Sem isso as moedas não
   servem para nada — e a mecânica inteira depende delas servirem.

Uma missão nunca pode existir num dia em que o despertador dela não toca: ela ficaria
eternamente pendente, sem nada capaz de cumpri-la. A regra é aplicada na gravação, não só
na interface — encolher os dias de um alarme poda as missões que ficaram órfãs.

**Seguir os dias do despertador** é o padrão porque o contrário falha em silêncio: mudar o
alarme de Seg–Sex para todo dia e descobrir semanas depois que as missões continuaram
presas nos dias antigos significa um despertador que toca sem cobrar nada. Seguindo, elas
acompanham a mudança nos dois sentidos — ganham os dias novos e perdem os que sumiram.
Quem tem dias próprios só perde os que sumiram; os dias novos continuam sendo escolha sua.

### Quanto cada missão vale

Toda missão tem seu próprio XP e suas próprias moedas, escolhidos no editor numa escala de
**5, 10, 20 ou 40**. O padrão é 10 e 10 nos dois: quem não quiser diferenciar nada não
precisa tocar em nada, e as missões já criadas continuam valendo o que valiam.

Mexer aqui é o que deixa "arrumar a cama" valer menos que "ir à academia". Os dois valores
são independentes de propósito, porque fazem coisas diferentes:

- **XP** é progresso de nível e nunca é perdido — nem no atraso, nem na falha, nem ao
  gastar moedas. Subir o XP de uma missão diz "isto é o que me faz avançar".
- **Moedas** são saldo gastável em prêmios, e são o que o atraso e a soneca cobram. Subir
  as moedas diz "isto merece uma recompensa maior".

A escala é curta em vez de um campo numérico livre por um motivo prático: com campo livre a
tentação é subir todo mundo, e quando tudo vale muito nada vale mais do que nada. Na lista
de missões, o valor só aparece quando foge do padrão — repetir "10 XP · 10 moedas" em toda
linha esconderia justamente as que você diferenciou.

Um detalhe que ninguém percebe até acontecer: **a sequência conta missões, não pontos.** O
limiar de 70% do dia é sobre quantidade cumprida, então uma missão de 40 XP e uma de 5
pesam igual para a sequência. Elas se diferenciam no nível e no saldo, não no que quebra ou
mantém a sua sequência.

## Dois tipos de despertador

O "+" do Relógio pergunta qual dos dois você quer:

| Tipo                 | Repete            | Missões | Some sozinho |
| -------------------- | ----------------- | ------- | ------------ |
| **Padrão**           | nos dias que você escolher | sim | não     |
| **Autodestruível**   | uma vez, na data marcada   | não | sim      |

O autodestruível é para o compromisso avulso: a consulta de quinta às 14h, o bolo que sai
do forno em 40 minutos. Você escolhe só a data — "Hoje" e "Amanhã" ficam à mão, e o
calendário completo a um toque. Depois que ele toca e você desliga, ele **se apaga**:
some da lista, do banco e do `AlarmManager`.

Por que apagar em vez de só desativar? Porque um alarme de uma vez só que ficasse na lista
viraria entulho, e uma lista de despertadores cheia de entulho deixa de ser confiável —
você para de saber, batendo o olho, o que vai tocar amanhã.

Ele **não aceita missões**, e isso não é uma limitação a contornar: a missão nasceria numa
cobrança que ninguém poderia liquidar depois, porque o dono dela deixaria de existir no
primeiro toque. O editor explica isso no lugar da lista de missões, em vez de esconder o
botão sem dizer por quê.

A autodestruição acontece ao desligar e também quando o alarme se cala sozinho depois de
cinco minutos sem ninguém atender — os dois jeitos de um toque acabar sem deixar outro
marcado. Adiar não conta: a soneca tem um disparo a caminho. E, como o aparelho pode estar
desligado na hora marcada, a varredura diária e o reagendamento em massa também recolhem os
autodestruíveis cuja data já passou, poupando os que estão em soneca.

## Desligar: totalmente ou só a próxima

A chave de um despertador respondia a uma pergunta só — "vale ou não vale?" — e era usada
para duas coisas bem diferentes: a folga de amanhã e o despertador que não serve mais. Quem
a usava para a folga precisava lembrar de religar, e descobria que esqueceu na manhã
seguinte, dormindo demais.

Agora desligar abre duas opções:

- **Desligar próxima ativação** — pula só o próximo toque, com uma linha em verde dizendo
  exatamente quando ele volta. Nada mais muda: o despertador continua ligado.
- **Desligar totalmente** — o comportamento antigo. Fica desligado até você ligar de novo.

Enquanto o pulo está em vigor, a linha do despertador mostra qual toque foi dispensado e
desfaz o pulo com um toque. Ela precisa existir: sem ela a chave ficaria ligada e o alarme
não tocaria amanhã, que é a falha mais assustadora que um app de despertador pode ter.

O que fica gravado é o **instante** pulado, não um "pular: sim"
([`skipNextFireAt`](app/src/main/java/dev/gianluca/alvoradaapp/data/Entities.kt)), e ele só
vale enquanto continuar batendo com o toque que aconteceria de fato. É isso que faz o pulo
caducar sozinho em todos os caminhos que importam — o horário passou, o despertador foi
reconfigurado, a recorrência mudou — sem nenhuma rotina de limpeza para alguém esquecer de
chamar. Pela mesma razão, só dá para pular um toque de cada vez: guardar um segundo
instante devolveria o primeiro à agenda em silêncio.

Um despertador autodestruível não oferece essa escolha. Pular a única ativação que ele tem
seria a mesma coisa que apagá-lo, e para isso já existe o botão de excluir.

## Repetição: quatro modos

O bitmask de sete dias resolve a maioria dos casos, mas não cabe "última sexta do mês" nem
"dia 15 e 30". Em vez de inventar um campo por formato, o tipo de repetição é explícito
([Recurrence.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/Recurrence.kt)) e cada
modo usa só os campos que lhe interessam:

| Modo                 | O que resolve                                                      |
| -------------------- | ------------------------------------------------------------------ |
| **Semanal**          | O padrão. Dias fixos, toda semana.                                 |
| **A cada N semanas** | Quinzenal e afins, com uma âncora que define a fase.               |
| **Semana do mês**    | 1ª a 5ª ocorrência, mais "última" — que **não** é o mesmo que "5ª". |
| **Dias do mês**      | Dia 15 e 30, ou o último dia do mês.                               |

No modo "dias do mês" o dia da semana é imprevisível, então as missões passam a valer
sempre que o despertador toca: restringi-las por dia da semana faria com que sumissem nos
meses em que o dia 15 cai numa terça.

Há um quinto valor no enum — o `ONCE` do autodestruível — que não aparece entre os chips.
Ele não é um modo de repetição, é a ausência de uma, e oferecer a conversão proporia algo
que o resto do app não sustenta: um despertador com missões viraria um sem elas.

O editor mostra uma prévia de **próximo disparo**. Ela existe para este bloco inteiro:
recorrência de calendário é fácil de configurar errado e impossível de conferir de cabeça.
Confira a data ali, em vez de descobrir daqui a três semanas que não tocou.

## Pontos, níveis e sequência

| Evento                       | XP        | Moedas                            |
| ---------------------------- | --------- | --------------------------------- |
| Missão no prazo              | cheio     | cheio                             |
| Missão tardia (via cobrança) | **cheio** | 60%                               |
| Missão não cumprida          | 0         | 0 — nunca negativo                |
| Soneca                       | 0         | −5, nunca abaixo de zero no saldo |
| Dia perfeito                 | +25       | +25                               |

"Cheio" é o valor daquela missão, definido no editor — 10 e 10 por padrão. Ver
[Quanto cada missão vale](#quanto-cada-missão-vale).

**Níveis.** A curva é quadrática, não exponencial: `XP para o nível L = 25 · L · (L−1)`.
Os primeiros níveis vêm rápido, quando ainda não há hábito para sustentar a motivação, e o
espaçamento cresce de forma previsível — nunca ao ponto de o próximo parecer inalcançável.

**XP nunca decresce.** Nada no app o remove: atraso e soneca custam moedas, falha não custa
nada, e comprar prêmio não mexe no nível. O nível registra o que você já fez, e o passado
não deixa de ter acontecido por causa de uma semana ruim.

**Sequência.** Um dia conta com 70% das missões cumpridas, não 100% — exigir o dia perfeito
faz a primeira missão perdida esvaziar o sentido de tentar as outras. Dia sem missão
nenhuma é neutro: não avança nem quebra, porque punir você por ter descansado de propósito
seria absurdo. E **dois escudos por mês** absorvem os tropeços: uma sequência de 47 dias
que zera por um dia ruim é a principal causa de abandono deste tipo de app. O escudo
transforma o tropeço em custo, não em colapso — e o Painel mostra quantos restam, porque
uma rede invisível não protege ninguém do desânimo.

Sequência e dia perfeito só são avaliados para dias **encerrados**, pela varredura das 03h.

Os números estão em [Leveling.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/Leveling.kt),
[StreakRules.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/StreakRules.kt) e nas
constantes de [PointsRepository.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/PointsRepository.kt).
Foram escolhidos no escuro e provavelmente vão querer ajuste depois de algumas semanas de
uso real.

## Backup

Um único ZIP, exportado em Ajustes, com `alvorada-backup.json` mais as fotos em
`evidence/<dia>/`.

Isso não é luxo: as evidências moram no diretório privado do app, fora de qualquer backup
automático do Android. Ótimo para privacidade, péssimo para durabilidade — desinstalar
apaga meses de registro sem aviso, e este arquivo é a única cópia que sobrevive.

A restauração **substitui** tudo em vez de mesclar. Mesclar exigiria decidir o que fazer
com ids repetidos, missões editadas dos dois lados e fotos duplicadas — regras que
falhariam de formas difíceis de perceber. Substituir cabe inteiro num aviso de uma linha.

Dois detalhes que o código trata e que quebrariam em silêncio se ignorados: o caminho das
fotos é guardado **relativo** e reancorado na importação (o diretório privado muda a cada
instalação, então um caminho absoluto restauraria para o nada); e os agendamentos são
reconstruídos no fim, porque o banco restaurado tem despertadores diferentes dos que
estavam registrados no `AlarmManager`.

## Decisões de design

As que mais moldam o código, com o motivo:

**O alarme usa `setAlarmClock`, sempre.** É o único caminho isento do Doze — o sistema
nunca adia um alarme registrado como despertador. O custo é o ícone de alarme na barra de
status, que é justamente a confirmação visual de que existe algo agendado.

**Toda escrita em alarme passa pelo `AlarmRepository`.** Gravar no banco sem reagendar
deixaria o banco dizendo uma coisa e o `AlarmManager` fazendo outra. O repositório torna
esse par indivisível.

**Sons escolhidos pelo seletor são copiados para dentro do app.** Uma URI do SAF depende de
uma permissão revogável e de um arquivo que pode ser movido ou apagado; em qualquer um dos
casos o despertador acordaria mudo. Copiar custa alguns megabytes e elimina a classe
inteira de falha.

**Quem acende a tela é um wake lock, não a Activity.** O `setTurnScreenOn` nunca foi
suficiente com o aparelho em Doze: o display só liga fisicamente com um wake lock de tela
com `ACQUIRE_CAUSES_WAKEUP`, segurado pelo serviço.

**Não existe alarme sem saída.** O app não pode ter um estado em que o aparelho toca e não
há onde desligar. São três caminhos independentes: a tela do alarme, os botões na
notificação, e a tela subindo por cima sozinha se você abrir o app enquanto toca. Desativar
ou excluir o despertador durante o toque também cala o som na hora.

**Estado temporário é guardado como o instante em que ele expira, não como um
sinalizador.** O pulo de um toque grava *qual* toque foi pulado; quando esse instante deixa
de coincidir com o próximo disparo, o pulo simplesmente para de valer. A alternativa —
`skipNext: Boolean` — exigiria limpar a flag na hora certa em cada caminho (o disparo, a
edição, o reboot, a troca de fuso), e o caminho esquecido seria um despertador que não toca
sem explicação.

**Um despertador de uma vez só se apaga em vez de se desativar.** Desativado, ele ficaria
na lista para sempre, e uma lista com entulho deixa de responder de relance o que vai tocar
amanhã. Como nem todo caminho passa pelo botão de desligar, o reagendamento em massa
também recolhe os que venceram.

**As missões do dia são materializadas ao dispensar o alarme, não à meia-noite.** Assim uma
missão editada de manhã já vale para o disparo daquele dia, e um dia em que o alarme não
tocou não gera pendência nenhuma.

**A cobrança é cancelada assim que a última foto chega**, por qualquer caminho — pela tela
do alarme, pelo Painel ou pela aba Evidências. Ser cobrado por algo já feito é o atrito que
faria qualquer um desinstalar o app.

**Saldo e nível são sempre derivados do extrato**, nunca campos guardados. Concluir a mesma
missão duas vezes pontua uma vez só. Se o saldo pudesse divergir do histórico, a confiança
no mecanismo inteiro iria junto.

**Missão não cumprida é cinza, nunca vermelha.** É informação, não repreensão.

**Injeção de dependência manual.** Hilt resolveria o mesmo problema com anotações e geração
de código; num app deste porte isso troca clareza por magia. O grafo inteiro se lê de uma
vez em [AlvoradaApp.kt](app/src/main/java/dev/gianluca/alvoradaapp/AlvoradaApp.kt).

## Arquitetura

Uma Activity para o app, uma para o alarme, Compose em cima, Room embaixo, sem servidor.

```
MainActivity ──> AlvoradaNavigation ──> telas Compose
                                            │
AlarmReceiver ──> AlarmService ──> AlarmActivity
      ▲                │
      │                ▼
AlarmScheduler ◄── repositórios ──> Room (AlvoradaDatabase)
      │
   AlarmManager
```

**A cadeia de disparo.** O `AlarmManager` acorda o
[AlarmReceiver](app/src/main/java/dev/gianluca/alvoradaapp/alarm/AlarmReceiver.kt), que
sobe o [AlarmService](app/src/main/java/dev/gianluca/alvoradaapp/alarm/AlarmService.kt) em
foreground. O serviço é o dono do alarme enquanto ele toca: segura os wake locks, o áudio e
a notificação, e é a fonte de verdade que a
[AlarmActivity](app/src/main/java/dev/gianluca/alvoradaapp/alarm/AlarmActivity.kt) observa.
A Activity é só a cara do que o serviço está fazendo — por isso fechá-la não cala o som.

| Peça                                                | Arquivo                                                                                                                    |
| --------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Agendamento, soneca, cobrança, reagendamento em massa | [AlarmScheduler.kt](app/src/main/java/dev/gianluca/alvoradaapp/alarm/AlarmScheduler.kt)                                   |
| Cálculo de próximo disparo (puro, testado)          | [NextFireCalculator.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/NextFireCalculator.kt)                              |
| Recorrência de calendário (puro, testado)           | [Recurrence.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/Recurrence.kt)                                              |
| Curva de nível e regras de sequência (puros, testados) | [Leveling.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/Leveling.kt) · [StreakRules.kt](app/src/main/java/dev/gianluca/alvoradaapp/core/StreakRules.kt) |
| Reagendamento pós-reboot                            | [BootReceiver.kt](app/src/main/java/dev/gianluca/alvoradaapp/alarm/BootReceiver.kt)                                        |
| Áudio no canal de alarme                            | [AlarmSoundPlayer.kt](app/src/main/java/dev/gianluca/alvoradaapp/alarm/AlarmSoundPlayer.kt)                                 |
| Importação de som próprio                           | [AlarmSoundStore.kt](app/src/main/java/dev/gianluca/alvoradaapp/alarm/AlarmSoundStore.kt)                                   |
| Schema completo                                     | [Entities.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/Entities.kt)                                                  |
| Banco e migrations                                  | [AlvoradaDatabase.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/AlvoradaDatabase.kt)                                  |
| CRUD de alarme + efeitos de agendamento             | [AlarmRepository.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/AlarmRepository.kt)                                    |
| Missões, evidência, cobrança                        | [MissionRepository.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/MissionRepository.kt)                                |
| Extrato, saldo, sequência, resgates                 | [PointsRepository.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/PointsRepository.kt)                                  |
| Agregação de leitura do Painel                      | [PanelRepository.kt](app/src/main/java/dev/gianluca/alvoradaapp/data/PanelRepository.kt)                                    |
| Varredura de virada de dia                          | [DailySweepWorker.kt](app/src/main/java/dev/gianluca/alvoradaapp/work/DailySweepWorker.kt)                                  |
| Arquivos de evidência                               | [EvidenceStore.kt](app/src/main/java/dev/gianluca/alvoradaapp/evidence/EvidenceStore.kt)                                    |
| Export e restauração                                | [BackupManager.kt](app/src/main/java/dev/gianluca/alvoradaapp/backup/BackupManager.kt)                                      |
| Ajustes e diagnóstico                               | [DiagnosticsScreen.kt](app/src/main/java/dev/gianluca/alvoradaapp/diagnostics/DiagnosticsScreen.kt) · [SystemChecks.kt](app/src/main/java/dev/gianluca/alvoradaapp/diagnostics/SystemChecks.kt) |
| Boas-vindas e aviso de pendências                   | [WelcomeDialog.kt](app/src/main/java/dev/gianluca/alvoradaapp/ui/onboarding/WelcomeDialog.kt) · [SetupCard.kt](app/src/main/java/dev/gianluca/alvoradaapp/ui/onboarding/SetupCard.kt) |

**Convenções do schema:** instantes absolutos são epoch millis (`Long`); datas civis são
`String` ISO `yyyy-MM-dd`, que ordena lexicograficamente e serve direto como chave de
agrupamento na galeria; dias da semana são bitmask `Int`, bit 0 = segunda.

**Modelo de dados**, em uma frase cada: `folders` agrupa `alarms`, que têm `missions`; cada
disparo grava uma `alarm_occurrence`, que materializa `mission_instances` no dia; cada
instância acumula `evidence_photos` e credita `points_ledger`; `rewards` e
`reward_redemptions` gastam o saldo; `streak_state` é uma linha só.

**Identidade.** O `applicationId` e o pacote Kotlin são `dev.gianluca.alvoradaapp`,
renomeados a partir do antigo `dev.gianluca.wakeapp`. O Android trata `applicationId`
diferente como app diferente: uma instalação anterior sobrevive lado a lado, com os dados
dela. Ninguém nunca vê essa string — quem aparece na tela inicial é o `app_name`.

**Identidade visual.** O logo original está em [`design/`](design/), em branco e preto sobre
transparência, e os assets saem dele por script porque cada destino tem uma regra de
enquadramento própria:

| Asset                        | Enquadramento        | Por quê                                                                                                                 |
| ---------------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `ic_launcher_foreground.png` | logo a 54 de 108dp   | O launcher descarta 18dp por borda **antes** da máscara. A 64dp o sino e as fotos são cortados no recorte circular.      |
| `splash_logo.png`            | logo a 62% do quadro | A splash do Android 12+ quer o conteúdo em 2/3 de 288dp. Reusar o foreground mostraria um logo minúsculo.                |
| `logo_alvorada.png`          | logo cru, 512px      | Usado no card "Sobre", tingido pela cor do tema. Branco puro com alfa serve aos temas claro e escuro com um arquivo só.  |

O ícone das notificações **não** usa o logo: o Android o desenha como silhueta chapada de
24dp, usando só o canal alfa, e o relógio, a seta, a montanha e as fotos viram uma mancha.
Ali segue o `ic_alarm`.

## Como testar

### Sem aparelho

```bash
./gradlew testDebugUnitTest
```

58 testes cobrindo o que é puro e onde moram os erros que só apareceriam meses depois:
cálculo de próximo disparo, os quatro modos de recorrência, a data única do autodestruível,
o pulo de um toque, bitmask de dias, curva de nível e regras de sequência.

### No aparelho

O roteiro abaixo é o conjunto mínimo que prova que o app funciona. Os testes marcados com
★ são os que mais falham e os que mais importam.

**O despertador toca?**

| #  | Teste                                                                    | Esperado                                            |
| -- | ------------------------------------------------------------------------ | --------------------------------------------------- |
| 1 | Ajustes → "Em 1 minuto", bloquear a tela, guardar o celular              | Tela acende sozinha, som por cima do bloqueio       |
| 2 | Repetir com o app morto: `adb shell am force-stop dev.gianluca.alvoradaapp.debug` | Idem                                       |
| 3 ★ | Ajustes → "Criar p/ daqui a 5 min", **reiniciar o celular**, esperar     | Toca mesmo assim (`BootReceiver` reagendou)         |
| 4 | Repetir sob `adb shell dumpsys deviceidle force-idle`                    | Toca no horário, sem atraso                         |
| 5 | Ativar silencioso e Não Perturbe, repetir o 1                            | Som toca assim mesmo                                |
| 6 | Desligar o alarme                                                        | A tela não apaga na cara; permanece o tempo normal  |

O teste 3 é o que mais importa: os testes 1 e 2 usam o alarme de teste, que não passa pelo
banco; o 3 usa um despertador real, gravado e reagendado após o reboot.

**Não existe alarme sem saída**

| #  | Teste                                       | Esperado                                                            |
| -- | ------------------------------------------- | ------------------------------------------------------------------- |
| 7 | Tocando, sair da tela do alarme (Home)      | A notificação traz **Soneca N min** e **Desligar**, ambos funcionam |
| 8 | Com o teto de sonecas atingido              | Só **Desligar** — o botão não aparece para ser recusado             |
| 9 | Tocando, abrir o app pelo ícone             | A tela do alarme sobe por cima, obrigatoriamente                    |
| 10 | Tocando, desativar a chave do despertador   | O som para na hora                                                  |
| 11 | Tocando, excluir o despertador ou a pasta   | O som para na hora                                                  |

**A cobrança funciona?** Configure uma missão com foto, prazo de 5 min, teto de 2 cobranças,
num despertador para daqui a 2 minutos.

| #    | Teste                                            | Esperado                                                    |
| ---- | ------------------------------------------------ | ----------------------------------------------------------- |
| 12 | Desligar o alarme sem fotografar                 | A tela **não some** — mostra a pendência e o prazo correndo |
| 13 | Tocar em "Fazer depois" e esperar os 5 min       | O despertador volta a tocar em modo cobrança                |
| 14 | Fotografar na cobrança e concluir                | A tela fecha; nada mais toca                                |
| 15 | Deixar estourar as 2 cobranças sem foto          | Para de insistir; a missão vira não cumprida                |
| 16 ★ | Enviar a foto pela aba Evidências, não pela tela | A cobrança agendada é **cancelada** — nada toca depois      |
| 17 | Missão com 1 de 2 fotos, sem concluir            | Continua em "Faltando"; o botão vira "Abrir"                |
| 18 | Encolher os dias do despertador                  | Missões em dias órfãos são podadas                          |

**Pontos e sequência**

| #    | Teste                               | Esperado                                            |
| ---- | ----------------------------------- | --------------------------------------------------- |
| 19 | Cumprir missão no prazo             | XP e moedas cheios; o Painel atualiza na hora       |
| 20 | Cumprir depois da cobrança          | XP cheio, moedas em 60%                             |
| 21 ★ | Concluir a mesma missão duas vezes  | Pontua **uma só vez** — o extrato é idempotente     |
| 22 | Dar soneca                          | −5 moedas; XP intacto                               |
| 23 | Soneca com saldo zero               | O saldo fica em 0, não vai a negativo               |
| 24 | Resgatar prêmio dentro do saldo     | O saldo cai; nível e XP não mudam                   |
| 25 | Missão com 40 XP / 5 moedas         | Credita 40 e 5; a lista de missões mostra o valor   |
| 26 | Ajustes → "Rodar agora"             | Fecha pendências vencidas e reavalia a sequência    |

Sequência e dia perfeito só valem para dias encerrados — para testá-los sem esperar a
virada do dia, use o botão de varredura em Ajustes.

**Recorrência** (confira sempre pela prévia "próximo disparo" do editor)

| #  | Teste                                | Esperado                                                        |
| -- | ------------------------------------ | --------------------------------------------------------------- |
| 27 | "A cada N semanas", sexta, 2 semanas | A prévia mostra esta sexta; a seguinte é 14 dias depois         |
| 28 | "Semana do mês" → sexta + Última     | Cai na última sexta do mês, não na quarta                       |
| 29 | "Dias do mês" → só 31, em fevereiro  | Pula o mês; a prévia mostra 31/03                               |
| 30 | Salvar sem nenhuma ocorrência marcada| Botão desabilitado, com aviso do que falta                      |
| 31 | Reiniciar o celular                  | Os quatro modos voltam agendados                                |

**Desligar, pular e autodestruir**

| #    | Teste                                                              | Esperado                                                                   |
| ---- | ------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| 32 | Desligar a chave de um despertador diário                          | Abre a escolha; "próxima ativação" mostra em verde quando ele volta        |
| 33 ★ | Escolher "Desligar próxima ativação" e reiniciar o celular         | Continua pulado; a contagem aponta para o toque **seguinte**               |
| 34 | Tocar na linha verde "…pulado"                                     | Desfaz o pulo; a contagem volta para o toque original                      |
| 35 | Deixar o toque pulado passar                                       | O pulo caduca sozinho; o toque seguinte acontece normalmente               |
| 36 | Criar um autodestruível para daqui a 2 min e desligá-lo ao tocar   | Desaparece da lista imediatamente                                          |
| 37 | Criar um autodestruível e não atender                              | Cala em 5 min e desaparece assim mesmo                                     |
| 38 | Autodestruível: dar soneca                                         | **Não** desaparece — a soneca tem um toque a caminho                       |
| 39 | Missão com "seguir os dias" marcada; mudar o alarme para todo dia  | A missão passa a valer todo dia, sem abrir o editor dela                   |
| 40 | Missão com dias próprios; mudar o alarme para todo dia             | Mantém os dias dela; só perde dias que o alarme deixou de ter              |

O teste 33 é o que mais importa aqui: o pulo não vive na memória do app nem num
`PendingIntent` cancelado — o que está agendado no sistema já é o toque seguinte, então um
reboot reconstrói exatamente o mesmo estado.

**Backup**

| #    | Teste                                  | Esperado                                                       |
| ---- | -------------------------------------- | -------------------------------------------------------------- |
| 41 | Ajustes → Exportar                     | Salva `alvorada-backup-AAAA-MM-DD.zip` onde você escolher      |
| 42 ★ | **Desinstalar, reinstalar, restaurar** | Despertadores, pontos e fotos de volta; alarmes reagendados    |
| 43 | Restaurar um ZIP qualquer              | Recusa com "não parece um backup do Alvorada", sem apagar nada |
| 44 | Restaurar um `wake-backup-*.zip` antigo| Funciona: a leitura aceita o manifesto dos dois nomes          |

O teste 42 é o único que prova a feature de verdade — é o cenário para o qual ela existe, e
o que revelaria uma regressão no caminho relativo das fotos. O 43 importa quase tanto: a
descompactação vai para uma área temporária antes de o banco ser tocado, então um arquivo
corrompido não pode destruir o que já existe.

**Som próprio**

| #  | Teste                                                                   | Esperado                    |
| -- | ----------------------------------------------------------------------- | --------------------------- |
| 45 | Escolher um MP3 seu, **apagar o arquivo original** e disparar o alarme  | Continua tocando normalmente |

Logs úteis durante os testes:

```bash
adb logcat -s AlarmScheduler:I AlarmReceiver:I AlarmService:I BootReceiver:I \
             MissionRepository:I PointsRepository:I DailySweepWorker:I
```

## Quando o alarme não toca

Na ordem, do mais provável ao menos:

1. **Abra Ajustes** e resolva tudo que estiver com aviso. Cinco permissões, todas
   silenciosas.
2. **Otimização de bateria** é a causa número um. Precisa estar desativada para este app.
3. **Aviso do fabricante**, se aparecer na tela: Xiaomi, Samsung e Huawei matam apps
   dormindo mesmo com todas as permissões do Android concedidas.
4. **Falhou só com o aparelho em Doze** (teste 4)? É permissão de alarme exato.
5. **Falhou só depois de reiniciar** (teste 3)? É o fabricante engolindo o `BOOT_COMPLETED`.
   O app tem uma rede de segurança que reagenda tudo ao abrir, mas ela só ajuda se você
   abrir o app.
6. **Tocou mas a tela não acendeu?** É o canal "Despertadores" rebaixado ou a permissão de
   tela cheia sobre o bloqueio.

## Desenvolvimento

**Stack:** Kotlin, Jetpack Compose (Material 3), Room + KSP, WorkManager, CameraX, Coil,
Navigation Compose, kotlinx.serialization. `minSdk 29`, `targetSdk 36`, Java 17.

**Setup:** instale o [Android Studio](https://developer.android.com/studio) — ele traz o
JDK embutido (JBR 21) e baixa o SDK sozinho. `File → Open` na raiz do projeto, aguarde o
Gradle sync (ele cria o `local.properties` com o caminho do SDK) e dê `Run`.

O `gradle/wrapper/gradle-wrapper.jar` não está versionado por ser binário. O Studio o
regenera no primeiro sync a partir do
[gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties); com Gradle
instalado, `gradle wrapper` também resolve.

Por linha de comando, depois do primeiro sync:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # confira o caminho real
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O build debug leva o sufixo `.debug` no `applicationId`, então a versão de debug e a de
release convivem no mesmo aparelho.

**Migrations.** O banco está na **versão 5** e o schema é exportado para
[`app/schemas/`](app/schemas/). A partir da versão 3 toda mudança exige migration explícita
— `fallbackToDestructiveMigration` vale só para downgrade, então avançar sem migration é
erro de build, não perda silenciosa de dados. Exporte um backup antes de instalar uma
versão que mexe no schema.

**Testes.** Ficam em [`app/src/test/`](app/src/test/java/dev/gianluca/alvoradaapp/core/) e
cobrem só o núcleo puro — de propósito. Agendamento, recorrência e pontuação são onde os
erros aparecem meses depois; o resto se verifica mais barato no aparelho.

## Limitações e próximos passos

- **Os números da gamificação foram escolhidos no escuro.** XP por missão, limiar de 70%,
  60% de moedas no atraso, custo da soneca — depois de algumas semanas de uso dá para saber
  se o app está frouxo ou punitivo demais.
- **O backup é manual.** Um `WorkManager` semanal gravando numa pasta escolhida uma vez
  resolveria o esquecimento, que é o modo de falha real de backup manual.
- **Sem sincronização entre aparelhos**, e não é um objetivo: o app é local por decisão, e o
  ZIP de backup é a ponte.
- **Fabricantes agressivos** continuam sendo o maior risco à confiabilidade, e não há nada
  que o app possa fazer além de avisar.
