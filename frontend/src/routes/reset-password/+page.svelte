<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { updatePassword } from '$lib/api';
	import { userStore } from '$lib/stores/user';

	let token = '';
	let newPassword = '';
	let confirmPassword = '';
	let error = '';
	let success = '';
	let tokenMissing = false;

	onMount(() => {
		// Token is passed in the URL fragment (#token=...) to prevent leakage via Referer header
		const hash = window.location.hash.slice(1); // remove leading '#'
		const params = new URLSearchParams(hash);
		token = params.get('token') || '';
		if (!token) {
			tokenMissing = true;
		}
	});

	async function handlePasswordReset() {
		error = '';
		success = '';

		if (!newPassword || newPassword.length < 8) {
			error = 'Password must be at least 8 characters';
			return;
		}

		if (newPassword !== confirmPassword) {
			error = 'Passwords do not match';
			return;
		}

		try {
		await updatePassword(token, newPassword);
		success = 'Password updated successfully! Redirecting to login...';
		await userStore.logout();
		setTimeout(() => goto('/', { replaceState: true }), 2000);
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to update password';
		}
	}
</script>

<svelte:head>
	<title>Reset Password - Medicate</title>
</svelte:head>

<div class="max-w-md mx-auto mt-8">
	<div class="bg-white border border-black p-6 rounded-tr-lg rounded-bl-lg">
		<h2 class="text-2xl font-bold mb-6">Reset Your Password</h2>

		{#if tokenMissing}
			<div class="p-4 bg-red-50 border border-red-300 text-red-800 rounded mb-4">
				No reset token provided. Please use the link from your email.
			</div>
			<a href="/" class="btn btn-nav w-full text-center block">
				Back to Login
			</a>
		{:else if success}
			<div class="p-4 bg-green-50 border border-green-300 text-green-800 rounded mb-4">
				{success}
			</div>
		{:else}
			<form on:submit|preventDefault={handlePasswordReset}>
				<div class="mb-4">
					<label for="new-password" class="block mb-1 font-semibold">New Password</label>
					<input
						id="new-password"
						type="password"
						bind:value={newPassword}
						class="input w-full"
						placeholder="Enter new password"
						required
						minlength="8"
					/>
					<p class="text-xs text-gray-600 mt-1">Minimum 8 characters</p>
				</div>

				<div class="mb-4">
					<label for="confirm-password" class="block mb-1 font-semibold">Confirm Password</label>
					<input
						id="confirm-password"
						type="password"
						bind:value={confirmPassword}
						class="input w-full"
						placeholder="Confirm new password"
						required
						minlength="8"
					/>
				</div>

				{#if error}
					<div class="mb-4 p-3 bg-red-50 border border-red-300 text-red-800 text-sm rounded">
						{error}
					</div>
				{/if}

				<button type="submit" class="btn btn-nav w-full">
					Reset Password
				</button>
			</form>

			<div class="mt-4 text-center text-sm">
				<a href="/" class="text-[steelblue] hover:underline">
					Back to Login
				</a>
			</div>
		{/if}
	</div>
</div>
