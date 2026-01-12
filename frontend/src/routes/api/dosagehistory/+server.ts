import type { RequestHandler } from '@sveltejs/kit';

const BACKEND = 'http://localhost:8080/api/history';

export const GET: RequestHandler = async ({ fetch }) => {
  const res = await fetch(BACKEND);
  const data = await res.json();
  return new Response(JSON.stringify(data), { status: res.status, headers: { 'Content-Type': 'application/json' } });
};
