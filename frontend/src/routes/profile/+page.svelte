<script lang="ts">
	import { onMount } from 'svelte';
	import { getProfile, updateProfile, type User } from '$lib/api';
	import { userStore } from '$lib/stores/user';
	import { goto } from '$app/navigation';

	let user: User | null = null;
	let email = '';
	let firstName = '';
	let lastName = '';
	let loading = true;
	let saving = false;
	let error = '';
	let successMessage = '';

	onMount(async () => {
		if (!$userStore) {
			goto('/');
			return;
		}

		try {
			user = await getProfile();
			email = user.email || '';
			firstName = user.firstName || '';
			lastName = user.lastName || '';
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to load profile';
		} finally {
			loading = false;
		}
	});

	async function handleSubmit() {
		error = '';
		successMessage = '';

		if (!email.trim()) {
			error = 'Email is required';
			return;
		}

		if (!firstName.trim()) {
			error = 'First name is required';
			return;
		}

		if (!lastName.trim()) {
			error = 'Last name is required';
			return;
		}

		if (!email.includes('@')) {
			error = 'Please enter a valid email address';
			return;
		}

		saving = true;

		try {
			const updatedUser = await updateProfile(email, firstName, lastName);
			user = updatedUser;
			// Update user store
			userStore.set(updatedUser);
			successMessage = 'Profile updated successfully!';
			setTimeout(() => {
				successMessage = '';
			}, 3000);
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to update profile';
		} finally {
			saving = false;
		}
	}
</script>

<svelte:head>
	<title>Profile - Medicate</title>
</svelte:head>

<div class="container mx-auto px-4 py-8 max-w-2xl">
	<h1 class="text-3xl font-bold mb-6">Profile Settings</h1>

	{#if loading}
		<div class="flex justify-center items-center py-12">
			<div class="text-gray-600">Loading profile...</div>
		</div>
	{:else}
		<div class="bg-white rounded-lg shadow-md p-6">
			<form on:submit|preventDefault={handleSubmit}>
				<!-- Username (read-only) -->
				<div class="mb-6">
					<label for="username" class="block text-sm font-medium text-gray-700 mb-2">
						Username
					</label>
					<input
						type="text"
						id="username"
						value={user?.username || ''}
						disabled
						class="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-100 text-gray-600 cursor-not-allowed"
					/>
					<p class="mt-1 text-sm text-gray-500">Username cannot be changed</p>
				</div>

				<!-- Email -->
				<div class="mb-6">
					<label for="email" class="block text-sm font-medium text-gray-700 mb-2">
						Email Address <span class="text-red-500">*</span>
					</label>
					<input
						type="email"
						id="email"
						bind:value={email}
						required
						class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
						placeholder="your.email@example.com"
					/>
				</div>

				<!-- First Name -->
				<div class="mb-6">
					<label for="firstName" class="block text-sm font-medium text-gray-700 mb-2">
						First Name <span class="text-red-500">*</span>
					</label>
					<input
						type="text"
						id="firstName"
						bind:value={firstName}
						required
						class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
						placeholder="John"
					/>
				</div>

				<!-- Last Name -->
				<div class="mb-6">
					<label for="lastName" class="block text-sm font-medium text-gray-700 mb-2">
						Last Name <span class="text-red-500">*</span>
					</label>
					<input
						type="text"
						id="lastName"
						bind:value={lastName}
						required
						class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
						placeholder="Doe"
					/>
				</div>

				<!-- Error Message -->
				{#if error}
					<div class="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
						<p class="text-red-800 text-sm">{error}</p>
					</div>
				{/if}

				<!-- Success Message -->
				{#if successMessage}
					<div class="mb-4 p-4 bg-green-50 border border-green-200 rounded-lg">
						<p class="text-green-800 text-sm">{successMessage}</p>
					</div>
				{/if}

				<!-- Submit Button -->
				<div class="flex gap-4">
					<button
						type="submit"
						disabled={saving}
						class="flex-1 bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
					>
						{saving ? 'Saving...' : 'Save Changes'}
					</button>
					<button
						type="button"
						on:click={() => goto('/')}
						class="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
					>
						Cancel
					</button>
				</div>
			</form>
		</div>

		<!-- Additional Info -->
		<div class="mt-6 bg-blue-50 border border-blue-200 rounded-lg p-4">
			<h3 class="font-semibold text-blue-900 mb-2">💡 Profile Information</h3>
			<ul class="text-sm text-blue-800 space-y-1">
				<li>• Your first and last name will be used in password reset emails</li>
				<li>• Email address is required for password reset functionality</li>
				<li>• Username is permanent and cannot be changed</li>
			</ul>
		</div>
	{/if}
</div>
