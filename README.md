<p align="center"><img src="branding/sukirpc_logo.png" width="112" alt="SukiRPC" /></p>

<h1 align="center">SukiRPC</h1>
<p align="center">Discord Rich Presence para suas sessões de mídia no Android.</p>

<p align="center">
  <a href="https://github.com/Demmkkj/SukiRPC/releases/latest">Download</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="https://github.com/Demmkkj/SukiRPC/issues">Reportar problema</a>
</p>

## Genesis

A edição Genesis reúne o painel da atividade atual, o editor RPC Studio, o histórico Chronicle e os controles de privacidade do Prism. Confira as [notas do build 68](RELEASE-NOTES.md) e a [página de Releases](https://github.com/Demmkkj/SukiRPC/releases/latest) para ver o APK disponível.

| Onde você assiste | Como funciona |
| --- | --- |
| Android 8.0 ou superior | O app lê a sessão de mídia de integrações compatíveis após autorização de acesso às notificações. |
| Fire TV Stick | O Collector envia a atividade ao celular pela rede local. Pareie pelo QR ou pelo código da TV. |
| Outros dispositivos Android TV e Fire OS | A compatibilidade depende do aparelho, do sistema e da integração. |

## Instalação

1. Acesse a [página de Releases](https://github.com/Demmkkj/SukiRPC/releases/latest) e baixe o APK da sua arquitetura. Na maioria dos celulares Android atuais, use `arm64-v8a`. Em caso de dúvida, escolha `universal`.
2. No celular, autorize o acesso às notificações e selecione **Mobile** ou **Automático**.
3. Para usar na TV, instale o Collector, autorize o acesso às notificações e pareie os dispositivos na mesma rede. Depois selecione **TV** ou **Automático**.

## Privacidade

O Prism controla os campos compartilhados. O pareamento local usa TLS e verifica a identidade do Collector. A busca externa de capas é opcional e pode enviar o título da mídia ao catálogo configurado. Os detalhes e a capa dependem dos metadados fornecidos pelas integrações.

## Desenvolvimento

[Compilar o projeto](BUILDING.md) · [Licença MIT](LICENSE) · [Componentes de terceiros](THIRD_PARTY_NOTICES.md)

Projeto independente, sem afiliação com Discord, Nuvio ou Amazon.
