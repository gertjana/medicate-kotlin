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
	let toastMessage = '';
	let showToast = false;

	function showToastNotification(message: string) {
		toastMessage = message;
		showToast = true;
		setTimeout(() => {
			showToast = false;
		}, 3000);
	}

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

		// Robust email validation regex
		const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
		if (!emailRegex.test(email)) {
			error = 'Please enter a valid email address';
			return;
		}

		saving = true;

		try {
			const updatedUser = await updateProfile(email, firstName, lastName);
			user = updatedUser;
			// Update user store
			userStore.set(updatedUser);
			showToastNotification('Profile updated successfully!');
			// Redirect to main page after a short delay
			setTimeout(() => {
				goto('/');
			}, 1500);
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

				<!-- Submit Button -->
				<div class="flex gap-2">
					<button
						type="submit"
						disabled={saving}
						class="btn btn-primary"
					>
						{saving ? 'Saving...' : 'Save Changes'}
					</button>
					<button
						type="button"
						on:click={() => goto('/')}
						class="btn"
					>
						Cancel
					</button>
				</div>
			</form>
		</div>
	{/if}
</div>

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
