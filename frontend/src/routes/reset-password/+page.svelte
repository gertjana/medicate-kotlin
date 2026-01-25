<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { verifyResetToken, updatePassword, loginUser } from '$lib/api';
	import { userStore } from '$lib/stores/user';

	let token = '';
	let username = '';
	let newPassword = '';
	let confirmPassword = '';
	let error = '';
	let success = '';
	let loading = true;
	let tokenValid = false;

	onMount(async () => {
		// Get token from URL query parameter
		token = $page.url.searchParams.get('token') || '';

		if (!token) {
			error = 'No reset token provided';
			loading = false;
			return;
		}

		// Verify token
		try {
			const result = await verifyResetToken(token);
			username = result.username;
			tokenValid = true;
			loading = false;
		} catch (e) {
			error = e instanceof Error ? e.message : 'Invalid or expired reset token';
			loading = false;
		}
	});

	async function handlePasswordReset() {
		error = '';
		success = '';

		if (!newPassword || newPassword.length < 6) {
			error = 'Password must be at least 6 characters';
			return;
		}

		if (newPassword !== confirmPassword) {
			error = 'Passwords do not match';
			return;
		}

		try {
			await updatePassword(username, newPassword);
			success = 'Password updated successfully! Logging you in...';

			// Auto-login after successful password reset
			setTimeout(async () => {
				try {
					const user = await loginUser(username, newPassword);
					userStore.login(user);
					goto('/', { replaceState: true });
				} catch (e) {
					// If auto-login fails, redirect to login page
					goto('/', { replaceState: true });
				}
			}, 2000);
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

		{#if loading}
			<div class="text-center py-8">
				<p class="text-gray-600">Verifying reset token...</p>
			</div>
		{:else if !tokenValid}
			<div class="p-4 bg-red-50 border border-red-300 text-red-800 rounded mb-4">
				{error || 'Invalid or expired reset token'}
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
					<label for="username" class="block mb-1 font-semibold">Username</label>
					<input
						id="username"
						type="text"
						value={username}
						class="input w-full bg-gray-100"
						disabled
					/>
				</div>

			<div class="mb-4">
				<label for="new-password" class="block mb-1 font-semibold">New Password</label>
				<input
					id="new-password"
					type="password"
					bind:value={newPassword}
					class="input w-full"
					placeholder="Enter new password"
					required
					minlength="6"
				/>
				<p class="text-xs text-gray-600 mt-1">Minimum 6 characters</p>
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
						minlength="6"
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
