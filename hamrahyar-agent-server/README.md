# HamrahYar OpenAI agent backend

This service keeps the OpenAI API key on the server. The Android APK must never contain the OpenAI key.

## Environment

- `OPENAI_API_KEY`: server-side OpenAI API key.
- `HAMRAHYAR_CLIENT_TOKEN`: a separate random client token (at least 32 characters).
- `OPENAI_MODEL`: defaults to `gpt-5.5`.
- `PORT`: hosting provider port, defaults to 8080.

Install and run with Node.js 20+:

```sh
npm install
npm start
```

Health check: `GET /health`

Agent endpoint: `POST /v1/agent/step` with `Authorization: Bearer <HAMRAHYAR_CLIENT_TOKEN>`.

## Important

This is the model-decision backend, not a complete deployed service. It must be deployed to a trusted HTTPS host and connected to the Android browser's observe/act loop before AI-driven browser actions work. Do not put either secret in Git, logs, the APK, or a website page. Configure secrets in the hosting provider's secret manager. OpenAI API usage may incur separate charges; have the account owner/guardian manage account access and billing.

The agent is intentionally prevented from handling passwords, one-time codes, identity checks, CAPTCHA, anti-bot queues, security challenges, or final irreversible submissions/payments. It can pause and ask the user to complete those steps.
