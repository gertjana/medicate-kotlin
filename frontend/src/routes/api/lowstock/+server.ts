import type { RequestHandler } from '@sveltejs/kit';

export const GET: RequestHandler = async ({ url, fetch }) => {
  const threshold = url.searchParams.get('threshold') ?? '10';
  const res = await fetch(`http://127.0.0.1:8080/api/lowstock?threshold=${threshold}`);
  const data = await res.json();
  return new Response(JSON.stringify(data), { status: res.status, headers: { 'Content-Type': 'application/json' } });
};
