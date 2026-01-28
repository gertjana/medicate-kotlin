<script lang="ts">
	import { onMount } from 'svelte';
	import { getProfile, updateProfile, type User } from '$lib/api';
	import { userStore } from '$lib/stores/user';
	import { goto } from '$app/navigation';
	import { _ } from 'svelte-i18n';

	let user: User | null = null;
	let email = '';
	let firstName = '';
	let lastName = '';
	let loading = true;
	let saving = false;
	let error = '';

	interface Toast {
		id: number;
		message: string;
	}
	let toasts: Toast[] = [];
	let toastIdCounter = 0;

	function showToastNotification(message: string) {
		const id = toastIdCounter++;
		toasts = [...toasts, { id, message }];
		setTimeout(() => {
			toasts = toasts.filter(t => t.id !== id);
		}, 6000);
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
			error = e instanceof Error ? e.message : $_('profile.loadFailed');
		} finally {
			loading = false;
		}
	});

	async function handleSubmit() {
		error = '';

		if (!email.trim()) {
			error = $_('profile.emailRequired');
			return;
		}

		if (!firstName.trim()) {
			error = $_('profile.firstNameRequired');
			return;
		}

		if (!lastName.trim()) {
			error = $_('profile.lastNameRequired');
			return;
		}

		const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
		if (!emailRegex.test(email)) {
			error = $_('profile.validEmailRequired');
			return;
		}

		saving = true;

		try {
			const updatedUser = await updateProfile(email, firstName, lastName);
			user = updatedUser;
			userStore.set(updatedUser);
			showToastNotification($_('profile.updateSuccess'));
			setTimeout(() => {
				goto('/');
			}, 1500);
		} catch (e) {
			error = e instanceof Error ? e.message : $_('profile.updateFailed');
		} finally {
			saving = false;
		}
	}
</script>

<svelte:head>
	<title>{$_('profile.title')} - Medicate</title>
</svelte:head>

<div class="container mx-auto px-4 py-8 max-w-2xl">
	<h1 class="text-3xl font-bold mb-6">{$_('profile.title')}</h1>

	{#if loading}
		<div class="flex justify-center items-center py-12">
			<div class="text-gray-600">{$_('profile.loading')}</div>
		</div>
	{:else}
		<div class="bg-white rounded-lg shadow-md p-6">
			<form on:submit|preventDefault={handleSubmit}>
				<!-- Username (read-only) -->
				<div class="mb-6">
					<label for="username" class="block text-sm font-medium text-gray-700 mb-2">
						{$_('profile.username')}
					</label>
					<input
						type="text"
						id="username"
						value={user?.username || ''}
						disabled
						class="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-100 text-gray-600 cursor-not-allowed"
					/>
					<p class="mt-1 text-sm text-gray-500">{$_('profile.usernameCannotChange')}</p>
				</div>

				<!-- Email -->
				<div class="mb-6">
					<label for="email" class="block text-sm font-medium text-gray-700 mb-2">
						{$_('profile.emailAddress')} <span class="text-red-500">{$_('profile.required')}</span>
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
						{$_('profile.firstName')} <span class="text-red-500">{$_('profile.required')}</span>
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
						{$_('profile.lastName')} <span class="text-red-500">{$_('profile.required')}</span>
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
						{saving ? $_('profile.saving') : $_('profile.save')}
					</button>
					<button
						type="button"
						on:click={() => goto('/')}
						class="btn"
					>
						{$_('profile.cancel')}
					</button>
				</div>
			</form>
		</div>
	{/if}
</div>

<div class="fixed top-[5.4rem] right-4 z-50 flex flex-col gap-2">
	{#each toasts as toast (toast.id)}
		<div class="animate-slide-up">
			<div class="p-4 rounded-lg shadow-lg border-2 bg-green-50 border-green-500 text-green-800">
				{toast.message}
			</div>
		</div>
	{/each}
</div>
