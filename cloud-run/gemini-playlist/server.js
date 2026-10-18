const http = require('http');
const {GoogleAuth} = require('google-auth-library');

const port = Number(process.env.PORT || 8080);
const projectId = process.env.GOOGLE_CLOUD_PROJECT || process.env.GCLOUD_PROJECT;
const location = process.env.GOOGLE_CLOUD_LOCATION || 'asia-northeast3';
const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
const maxBodyBytes = 16 * 1024;
const maxRequestsPerMinute = 10;
const requestWindows = new Map();

if (!projectId) {
  throw new Error('GOOGLE_CLOUD_PROJECT is required');
}

const auth = new GoogleAuth({
  scopes: ['https://www.googleapis.com/auth/cloud-platform'],
});

function sendJson(response, status, body) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
  });
  response.end(JSON.stringify(body));
}

function clientAddress(request) {
  const forwarded = request.headers['x-forwarded-for'];
  return (typeof forwarded === 'string' ? forwarded.split(',')[0] : request.socket.remoteAddress)
    || 'unknown';
}

function allowedByRateLimit(request) {
  const now = Date.now();
  const key = clientAddress(request);
  const window = requestWindows.get(key);
  if (!window || now - window.startedAt >= 60_000) {
    requestWindows.set(key, {startedAt: now, count: 1});
    return true;
  }
  if (window.count >= maxRequestsPerMinute) {
    return false;
  }
  window.count += 1;
  return true;
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let body = '';
    let bytes = 0;
    request.setEncoding('utf8');
    request.on('data', chunk => {
      bytes += Buffer.byteLength(chunk);
      if (bytes > maxBodyBytes) {
        reject(new Error('request too large'));
        request.destroy();
        return;
      }
      body += chunk;
    });
    request.on('end', () => resolve(body));
    request.on('error', reject);
  });
}

function extractQueries(text) {
  const cleaned = String(text || '').replace(/```json|```/gi, '').trim();
  const start = cleaned.indexOf('[');
  const end = cleaned.lastIndexOf(']');
  if (start < 0 || end <= start) {
    throw new Error('Gemini returned no query list');
  }
  const parsed = JSON.parse(cleaned.slice(start, end + 1));
  if (!Array.isArray(parsed)) {
    throw new Error('Gemini query response was not an array');
  }
  return parsed
    .filter(item => typeof item === 'string')
    .map(item => item.trim())
    .filter(Boolean)
    .slice(0, 10);
}

async function requestGemini(prompt) {
  const client = await auth.getClient();
  const tokenResponse = await client.getAccessToken();
  const accessToken = typeof tokenResponse === 'string' ? tokenResponse : tokenResponse.token;
  if (!accessToken) {
    throw new Error('Cloud authentication did not return an access token');
  }
  const endpoint = `https://${location}-aiplatform.googleapis.com/v1/projects/`
    + `${projectId}/locations/${location}/publishers/google/models/${model}:generateContent`;
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      contents: [{role: 'user', parts: [{text: 'Create a YouTube music playlist. Return only a JSON array '
        + 'of 10 concise search queries, one per song, for this request: ' + prompt}]}],
      generationConfig: {temperature: 0.6, responseMimeType: 'application/json'},
    }),
  });
  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(`Vertex AI HTTP ${response.status}: ${responseText.slice(0, 240)}`);
  }
  const payload = JSON.parse(responseText);
  const text = payload.candidates?.[0]?.content?.parts?.[0]?.text;
  return extractQueries(text);
}

const server = http.createServer(async (request, response) => {
  if (request.method === 'OPTIONS') {
    response.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
    });
    response.end();
    return;
  }
  if (request.method === 'GET' && request.url === '/health') {
    sendJson(response, 200, {ok: true});
    return;
  }
  if (request.method !== 'POST' || request.url !== '/v1/playlist') {
    sendJson(response, 404, {error: 'not found'});
    return;
  }
  if (!allowedByRateLimit(request)) {
    sendJson(response, 429, {error: 'rate limit exceeded'});
    return;
  }
  try {
    const body = JSON.parse(await readBody(request));
    const prompt = typeof body.prompt === 'string' ? body.prompt.trim() : '';
    if (!prompt || prompt.length > 500) {
      sendJson(response, 400, {error: 'prompt must contain 1-500 characters'});
      return;
    }
    sendJson(response, 200, {queries: await requestGemini(prompt)});
  } catch (error) {
    console.error('Gemini proxy request failed:', error.message);
    sendJson(response, 502, {error: 'Gemini request failed'});
  }
});

server.listen(port, '0.0.0.0', () => {
  console.log(`SimpleTunes Gemini proxy listening on ${port}`);
});
