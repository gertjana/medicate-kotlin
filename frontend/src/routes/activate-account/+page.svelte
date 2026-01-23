<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { userStore } from '$lib/stores/user';

	let status: 'loading' | 'success' | 'error' = 'loading';
	let errorMessage = '';

	onMount(async () => {
		const token = $page.url.searchParams.get('token');

		if (!token) {
			status = 'error';
			errorMessage = 'No activation token provided';
			return;
		}

		try {
			const response = await fetch('/api/auth/activateAccount', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({ token })
			});

			if (!response.ok) {
				const errorText = await response.text();
				throw new Error(errorText || 'Failed to activate account');
			}

			const data = await response.json();

			// Login the user with the returned token
			userStore.login({
				username: data.user.username,
				email: data.user.email,
				firstName: data.user.firstName,
				lastName: data.user.lastName
			});

			status = 'success';

			// Redirect to dashboard after 2 seconds
			setTimeout(() => {
				goto('/');
			}, 2000);
		} catch (e) {
			status = 'error';
			errorMessage = e instanceof Error ? e.message : 'Failed to activate account';
		}
	});
</script>

<svelte:head>
	<title>Activate Account - Medicate</title>
</svelte:head>

<div class="max-w-2xl mx-auto">
	{#if status === 'loading'}
		<div class="text-center py-12">
			<div class="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-gray-900 mb-4"></div>
			<h2 class="text-2xl font-bold mb-2">Activating Your Account</h2>
			<p class="text-gray-600">Please wait while we activate your account...</p>
		</div>
	{:else if status === 'success'}
		<div class="text-center py-12">
			<div class="mb-4 text-6xl">&#10003;</div>
			<h2 class="text-2xl font-bold mb-2 text-green-600">Account Activated Successfully</h2>
			<p class="text-gray-600 mb-4">Your account has been activated. You are now logged in.</p>
			<p class="text-sm text-gray-500">Redirecting to dashboard...</p>
		</div>
	{:else}
		<div class="text-center py-12">
			<div class="mb-4 text-6xl text-red-600">&#10007;</div>
			<h2 class="text-2xl font-bold mb-2 text-red-600">Activation Failed</h2>
			<p class="text-gray-600 mb-4">{errorMessage}</p>
			<div class="flex gap-2 justify-center">
				<a href="/" class="btn btn-nav">Go to Dashboard</a>
			</div>
		</div>
	{/if}
</div>
