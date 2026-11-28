# SukiRPC 2.1.2 · Genesis

**Build 68 · Android 8.0+**

Genesis reorganiza o SukiRPC em cinco áreas — Início, Extensões, RPC Studio, Chronicle e Ajustes — e reúne as correções da Operation Refinement para notificações, Collector e Rich Presence.

## Novidades

- **Genesis** — painel com atividade atual, progresso e prévia da presença. O Chronicle ganhou categorias, capas e filtros.
- **Horizon** — extensões de mídia agrupadas, com suporte a capas quando a sessão oferece metadados compatíveis. Uma capa no painel pode estar disponível apenas localmente.
- **Echo e Collector** — pareamento por QR ou código e captura mais estável de sessões na TV. O Collector lê o estado de cada sessão autorizada uma vez por amostra e fecha conexões ao desligar.
- **Prism e Discord** — a notificação passa a ocultar os campos configurados no Prism. Respostas antigas do SDK deixam de sobrescrever o estado da presença atual.
- **Aurora** — tema Genesis claro e escuro e modos Eco, Balanced e Ultra. O blur em tempo real fica no Ultra.

## Qual APK baixar?

| Arquivo | Dispositivo |
| --- | --- |
| `SukiRPC-arm64-v8a.apk` | Celulares Android modernos, incluindo o Redmi 14C |
| `SukiRPC-armeabi-v7a.apk` | Android ARM de 32 bits |
| `SukiRPC-x86.apk` | Android x86 de 32 bits |
| `SukiRPC-x86_64.apk` | Android x86 de 64 bits |
| `SukiRPC-universal.apk` | Qualquer uma das arquiteturas acima |

Para atualizar, instale o APK por cima da instalação atual. O Android exige uma assinatura compatível. Os arquivos `SHA256SUMS.txt` e `update.json` acompanham os APKs.

## Compatibilidade e validação

- Os cinco APKs e a configuração alternativa de R8 passaram no CI. Os testes de navegação e de inicialização da ponte JNI passaram no emulador Android 15.
- A exibição de capas no Discord depende dos metadados e do cliente. A nova rodada de testes físicos de pareamento no Fire TV e de desempenho em aparelhos de 4 GB continua pendente.
- Para relatar um problema, informe o modelo, a versão do Android e a integração utilizada. Remova códigos de pareamento, credenciais e dados privados do relato.

[Issues](https://github.com/Demmkkj/SukiRPC/issues) · [Changelog](https://github.com/Demmkkj/SukiRPC/blob/main/CHANGELOG.md)
