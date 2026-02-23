import type { RequestHandler } from '@sveltejs/kit';

const BACKEND = 'http://127.0.0.1:8080/api/takedose';

export const POST: RequestHandler = async ({ request, fetch }) => {
  const auth = request.headers.get('authorization') ?? '';
  const body = await request.text();
  const res = await fetch(BACKEND, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: auth },
    body
  });
  const data = await res.json();
  return new Response(JSON.stringify(data), { status: res.status, headers: { 'Content-Type': 'application/json' } });
};
