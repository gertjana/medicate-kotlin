import { writable } from 'svelte/store';
import { browser } from '$app/environment';
import type { User } from '$lib/api';
import { setAccessToken, getAccessToken } from '$lib/api';

const STORAGE_KEY = 'medicate_user';

function createUserStore() {
	const { subscribe, set } = writable<User | null>(null);

	return {
		subscribe,
		set,
		login: (user: User) => {
			if (browser) {
				localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
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
							const { default: api } = await import('$lib/api');
							// Try to refresh access token from cookie
							try {
								const response = await fetch('/api/auth/refresh', {
									method: 'POST',
									credentials: 'include'
								});
								if (response.ok) {
									const data = await response.json();
									setAccessToken(data.token);
								} else {
									// Refresh token expired or invalid, logout
									this.logout();
								}
							} catch (e) {
								console.error('Failed to refresh token on init:', e);
								this.logout();
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
