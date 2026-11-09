# HamrahYar OpenAI agent backend

This Node.js service keeps the OpenAI API key on the server. The Android APK calls this service over HTTPS; the OpenAI key is never sent to or stored in the app.

## Environment

- `OPENAI_API_KEY`: server-side OpenAI API key.
- `HAMRAHYAR_CLIENT_TOKEN`: a separate random client token (at least 32 characters).
- `OPENAI_MODEL`: defaults to `gpt-5.5`.
- `PORT`: hosting provider port, defaults to 8080.

Run with Node.js 20+:

```sh
npm install
npm start
```

Health check: `GET /health`

Agent endpoint: `POST /v1/agent/step` with `Authorization: Bearer <HAMRAHYAR_CLIENT_TOKEN>`.

## Android integration

The Android browser now:
1. Captures a bounded snapshot of visible ordinary form controls and page text.
2. Omits password, OTP, CAPTCHA, identity, queue/security and other sensitive controls.
3. Sends the task goal, snapshot and (only if the user opts in) selected profile fields to this backend.
4. Receives structured model actions and locally re-validates every fill/click before execution.
5. Re-observes the page between steps and stops after a bounded number of steps.

The app allows only ordinary field filling and clearly labelled next/continue navigation. It does not submit final forms, register accounts, purchase, pay, transfer, delete, bypass a queue, solve CAPTCHA, or handle authentication/security checks. The user must complete those gates and final actions.

## Deployment still required

The Android integration is committed, but this backend is **not deployed yet**. Deploy this folder to a trusted Node.js host with HTTPS, then configure the following server-side environment variables in the host's secret manager:

- `OPENAI_API_KEY`
- `HAMRAHYAR_CLIENT_TOKEN` (random value, at least 32 characters)
- optionally `OPENAI_MODEL`

Set the resulting HTTPS service URL and the separate client token in the app's start screen. Do not send either secret in chat or commit them to Git. The app does not persist the client token between launches. For production, replace the shared client token with short-lived per-user authentication; a shared token embedded or entered in a client app is not a strong production identity mechanism.

OpenAI API usage may incur separate charges. The account owner/guardian should manage account access and billing.
