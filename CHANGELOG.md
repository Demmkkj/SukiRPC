# Changelog

## SukiRPC 2.1.2 beta

- Painel de atividade com progresso e prévia da presença, extensões por categoria e histórico com capas e filtros.
- Editor de presença personalizada, opções de privacidade e temas claro e escuro.
- Notificações ocultam os campos selecionados nas preferências. Respostas antigas do SDK são descartadas ao trocar ou encerrar a atividade.
- O Collector reutiliza a leitura de cada sessão e encerra as conexões quando o serviço para.
- Pareamento por QR ou código. A exibição de capas depende da integração e do cliente Discord.

Compatível com Android 8.0 ou superior. Os cinco APKs e a configuração alternativa de R8 passaram no CI. A navegação e a inicialização JNI passaram no emulador Android 15. A validação física no Fire TV segue pendente.

[Downloads da versão beta](https://github.com/Demmkkj/SukiRPC/releases/tag/v2.1.2-beta)

## SukiRPC 1.0.0

Primeira versão pública estável, com Discord Rich Presence para Nuvio no celular e na TV.

- Sincronização local no Android e pareamento por QR ou código com o Collector no Fire TV Stick.
- Capas opcionais e progresso quando a sessão fornece esses dados.
- APKs ARM64, ARM32, x86, x86-64 e Universal. Requer Android 8.0 ou superior.

[Downloads](https://github.com/Demmkkj/SukiRPC/releases/tag/v1.0.0-build.30)
