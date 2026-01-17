import type { RequestHandler } from '@sveltejs/kit';

const BACKEND = 'http://127.0.0.1:8080/api/medicine';

export const GET: RequestHandler = async ({ fetch }) => {
  const res = await fetch(BACKEND);
  const data = await res.json();
  return new Response(JSON.stringify(data), { status: res.status, headers: { 'Content-Type': 'application/json' } });
};

export const POST: RequestHandler = async ({ request, fetch }) => {
  const body = await request.text();
  const res = await fetch(BACKEND, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body
  });
  const data = await res.json();
  return new Response(JSON.stringify(data), { status: res.status, headers: { 'Content-Type': 'application/json' } });
};
