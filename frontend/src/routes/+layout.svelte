<script lang="ts">
	import '../app.css';
	import { page } from '$app/stores';
	import { onMount } from 'svelte';
	import { userStore } from '$lib/stores/user';
	import { registerUser, loginUser } from '$lib/api';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	const navItems = [
		{ path: '/', label: 'Dashboard' },
		{ path: '/medicines', label: 'Medicines' },
		{ path: '/schedules', label: 'Schedules' },
		{ path: '/history', label: 'History' }
	];

	let showAuthModal = false;
	let authMode: 'login' | 'register' = 'login';
	let username = '';
	let authError = '';

	onMount(() => {
		userStore.init();
	});

	async function handleAuth() {
		authError = '';
		if (!username.trim()) {
			authError = 'Username is required';
			return;
		}

		try {
			const user = authMode === 'register'
				? await registerUser(username.trim())
				: await loginUser(username.trim());

			userStore.login(user);
			showAuthModal = false;
			username = '';
		} catch (e) {
			authError = e instanceof Error ? e.message : 'Authentication failed';
		}
	}

	function handleLogout() {
		userStore.logout();
	}

	function openAuthModal(mode: 'login' | 'register') {
		authMode = mode;
		username = '';
		authError = '';
		showAuthModal = true;
	}
</script>

<div class="min-h-screen flex flex-col">
	<header class="border-b border-black">
		<div class="pr-4 py-4">
			<div class="flex items-start gap-6">
				<img src="/medication.svg" alt="Medicine Scheduler Logo" class="h-24 w-24 flex-shrink-0 ml-4" style="filter: invert(48%) sepia(79%) saturate(2476%) hue-rotate(184deg) brightness(91%) contrast(87%);" />
				<div class="flex-1">
					<div class="flex justify-between items-start mb-4">
						<h1 class="text-2xl font-bold">Medicate</h1>
						<div class="flex items-center gap-2">
							{#if $userStore}
								<span class="text-sm font-semibold">👤 {$userStore.username}</span>
								<button on:click={handleLogout} class="btn text-xs">Logout</button>
							{:else}
								<button on:click={() => openAuthModal('login')} class="btn btn-primary text-xs">Login</button>
								<button on:click={() => openAuthModal('register')} class="btn text-xs">Register</button>
							{/if}
						</div>
					</div>
					<nav class="flex gap-2">
						{#each navItems as item}
							<a
								href={item.path}
								class="nav-link {$page.url.pathname === item.path ? 'nav-link-active' : ''}"
							>
								{item.label}
							</a>
						{/each}
					</nav>
				</div>
			</div>
		</div>
	</header>

	<main class="flex-1 container mx-auto px-4 py-8">
		<slot />
	</main>

	<footer class="border-t border-black">
		<div class="container mx-auto px-4 py-4 text-center text-sm text-gray-600">
			Medicate &copy; {new Date().getFullYear()}
		</div>
	</footer>
</div>

<!-- Auth Modal -->
{#if showAuthModal}
	<div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
		<div class="bg-white border border-black p-6 max-w-md w-full rounded-tr-lg rounded-bl-lg">
			<h3 class="text-xl font-bold mb-4">
				{authMode === 'register' ? 'Register' : 'Login'}
			</h3>
			<form on:submit|preventDefault={handleAuth}>
				<div class="mb-4">
					<label for="auth-username" class="block mb-1 font-semibold">Username</label>
					<input
						id="auth-username"
						type="text"
						bind:value={username}
						class="input w-full"
						placeholder="Enter your username"
						required
						autofocus
					/>
				</div>
				{#if authError}
					<div class="mb-4 p-3 bg-red-50 border border-red-300 text-red-800 text-sm rounded">
						{authError}
					</div>
				{/if}
				<div class="flex gap-2">
					<button type="submit" class="btn btn-primary flex-1">
						{authMode === 'register' ? 'Register' : 'Login'}
					</button>
					<button type="button" on:click={() => showAuthModal = false} class="btn flex-1">
						Cancel
					</button>
				</div>
				<div class="mt-4 text-center text-sm">
					{#if authMode === 'login'}
						<button
							type="button"
							on:click={() => { authMode = 'register'; authError = ''; }}
							class="text-[steelblue] hover:underline"
						>
							Don't have an account? Register
						</button>
					{:else}
						<button
							type="button"
							on:click={() => { authMode = 'login'; authError = ''; }}
							class="text-[steelblue] hover:underline"
						>
							Already have an account? Login
						</button>
					{/if}
				</div>
			</form>
		</div>
	</div>
{/if}
