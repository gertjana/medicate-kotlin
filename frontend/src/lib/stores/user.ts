import { writable } from 'svelte/store';
import { browser } from '$app/environment';
import type { User } from '$lib/api';

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
		logout: () => {
			if (browser) {
				localStorage.removeItem(STORAGE_KEY);
			}
			set(null);
		},
		init: () => {
			if (browser) {
				const stored = localStorage.getItem(STORAGE_KEY);
				if (stored) {
					try {
						set(JSON.parse(stored));
					} catch (e) {
						localStorage.removeItem(STORAGE_KEY);
					}
				}
			}
		}
	};
}

export const userStore = createUserStore();
