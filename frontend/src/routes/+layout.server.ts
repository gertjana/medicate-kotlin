import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
  // Optionally, you can preload user info here if you have session/cookie auth
  return {};
};
