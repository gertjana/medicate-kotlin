import { writable } from 'svelte/store';
import { browser } from '$app/environment';
import type { User } from '$lib/api';
import { setAccessToken, getAccessToken, parseIsAdminFromToken } from '$lib/api';

const STORAGE_KEY = 'medicate_user';

function createUserStore() {
	const { subscribe, set } = writable<User | null>(null);

	return {
		subscribe,
		set,
		login: (user: User) => {
			if (browser) {
				// Strip isAdmin before persisting — it is derived from the JWT token at runtime
				const { isAdmin: _stripped, ...userWithoutAdmin } = user;
				localStorage.setItem(STORAGE_KEY, JSON.stringify(userWithoutAdmin));
			}
			set(user);
		},
		logout: async () => {
			if (browser) {
				// Import logout dynamically to avoid circular dependency
				const { logout } = await import('$lib/api');
				await logout();
			}
			set(null);
		},
		init: async () => {
			if (browser) {
				const stored = localStorage.getItem(STORAGE_KEY);
				if (stored) {
					try {
						const user = JSON.parse(stored);
						set(user);

						// Access token is lost on page refresh (in memory)
						// Try to refresh it using the HttpOnly cookie
						if (!getAccessToken()) {
							// Try to refresh access token from cookie
							try {
								const response = await fetch('/api/auth/refresh', {
									method: 'POST',
									credentials: 'include'
								});
								if (response.ok) {
									const data = await response.json();
									setAccessToken(data.token);
									// Derive isAdmin from the fresh token — do not trust localStorage
									const isAdmin = parseIsAdminFromToken(data.token);
									set({ ...user, isAdmin });
								} else {
									// Refresh token expired or invalid, logout
									const { logout } = await import('$lib/api');
									await logout();
									set(null);
								}
							} catch (e) {
								console.error('Failed to refresh token on init:', e);
								const { logout } = await import('$lib/api');
								await logout();
								set(null);
							}
						}
					} catch (e) {
						localStorage.removeItem(STORAGE_KEY);
					}
				}
			}
		}
	};
}

export const userStore = createUserStore();
