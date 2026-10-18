# SimpleTunes Gemini Cloud Run proxy

This service calls Vertex AI with the Cloud Run runtime service account. The
Android APK never contains a Google Cloud API key or service-account JSON.

Expected runtime configuration:

- `GOOGLE_CLOUD_PROJECT`: the Google Cloud project ID
- `GOOGLE_CLOUD_LOCATION`: Vertex AI region (default `asia-northeast3`)
- `GEMINI_MODEL`: model name (default `gemini-2.5-flash`)

Endpoints:

- `GET /health`
- `POST /v1/playlist` with `{ "prompt": "..." }`

The current implementation includes a small per-instance rate limit for this
personal-use deployment. Cloud Run IAM/network restrictions should be added if
the service is exposed beyond the private APK.
