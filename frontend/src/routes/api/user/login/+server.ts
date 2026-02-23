import type { RequestHandler } from '@sveltejs/kit';

export const POST: RequestHandler = async ({ request, fetch }) => {
  const body = await request.text();
  const res = await fetch('http://127.0.0.1:8080/api/user/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body
  });
  const data = await res.json();

  // Forward Set-Cookie from backend so the HttpOnly refresh token cookie reaches the browser
  const responseHeaders = new Headers({ 'Content-Type': 'application/json' });
  const setCookie = res.headers.get('set-cookie');
  if (setCookie) {
    responseHeaders.set('set-cookie', setCookie);
  }

  return new Response(JSON.stringify(data), { status: res.status, headers: responseHeaders });
};
