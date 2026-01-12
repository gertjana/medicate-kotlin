import type { RequestHandler } from '@sveltejs/kit';

const BACKEND = 'http://localhost:8080/api/health';

export const GET: RequestHandler = async ({ fetch }) => {
  const res = await fetch(BACKEND);
  const data = await res.text();
  return new Response(data, { status: res.status, headers: { 'Content-Type': 'text/plain' } });
};
