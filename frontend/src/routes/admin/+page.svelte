<script lang="ts">
	import { onMount } from 'svelte';
	import { type AdminUser, getAllUsers, activateUser, deactivateUser, deleteUser } from '$lib/api';
	import { userStore } from '$lib/stores/user';
	import { goto } from '$app/navigation';
	import { _ } from 'svelte-i18n';

	let adminUsers: AdminUser[] = [];
	let loadingUsers = true;
	let adminError = '';
	let confirmDialog: { show: boolean; action: string; userId: string; username: string } = {
		show: false,
		action: '',
		userId: '',
		username: ''
	};

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
		// Wait a bit for userStore to initialize if needed
		let attempts = 0;
		while (!$userStore && attempts < 10) {
			await new Promise(resolve => setTimeout(resolve, 100));
			attempts++;
		}

		if (!$userStore) {
			goto('/');
			return;
		}

		if (!$userStore.isAdmin) {
			goto('/');
			return;
		}

		await loadUsers();
	});

	async function loadUsers() {
		loadingUsers = true;
		adminError = '';
		try {
			const response = await getAllUsers();
			console.log('Admin users response:', response);
			adminUsers = response.users;
			console.log('Admin users array:', adminUsers);
		} catch (e) {
			console.error('Failed to load users:', e);
			adminError = e instanceof Error ? e.message : $_('admin.loadFailed');
		} finally {
			loadingUsers = false;
		}
	}

	function showConfirm(action: string, userId: string, username: string) {
		confirmDialog = { show: true, action, userId, username };
	}

	function cancelConfirm() {
		confirmDialog = { show: false, action: '', userId: '', username: '' };
	}

	async function confirmAction() {
		const { action, userId, username } = confirmDialog;
		cancelConfirm();
		adminError = '';

		try {
			if (action === 'activate') {
				await activateUser(userId);
				showToastNotification($_('admin.userActivated', { values: { username } }));
			} else if (action === 'deactivate') {
				await deactivateUser(userId);
				showToastNotification($_('admin.userDeactivated', { values: { username } }));
			} else if (action === 'delete') {
				await deleteUser(userId);
				showToastNotification($_('admin.userDeleted', { values: { username } }));
			}
			await loadUsers();
		} catch (e) {
			adminError = e instanceof Error ? e.message : $_('admin.actionFailed', { values: { action } });
		}
	}
</script>

<svelte:head>
	<title>{$_('admin.title')} - Medicate</title>
</svelte:head>

<div class="container mx-auto px-4 py-8 max-w-7xl">
	<div class="mb-6">
		<h1 class="text-3xl font-bold">{$_('admin.title')}</h1>
	</div>

	{#if adminError}
		<div class="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
			<p class="text-red-800 text-sm">{adminError}</p>
		</div>
	{/if}

	{#if loadingUsers}
		<div class="flex justify-center items-center py-12">
			<div class="text-gray-600">{$_('admin.loadingUsers')}</div>
		</div>
	{:else}
		<div class="bg-white rounded-lg shadow-md overflow-hidden">
			<div class="overflow-x-auto">
				<table class="min-w-full divide-y divide-gray-200">
					<thead class="bg-gray-50">
						<tr>
							<th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{$_('admin.username')}</th>
							<th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{$_('admin.email')}</th>
							<th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{$_('admin.name')}</th>
							<th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{$_('admin.status')}</th>
							<th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{$_('admin.role')}</th>
							<th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{$_('admin.actions')}</th>
						</tr>
					</thead>
					<tbody class="bg-white divide-y divide-gray-200">
						{#each adminUsers as adminUser}
							<tr class="{adminUser.isSelf ? 'bg-blue-50' : ''}">
								<td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
									{adminUser.username}
									{#if adminUser.isSelf}
										<span class="ml-1 text-xs text-blue-600">({$_('admin.you')})</span>
									{/if}
								</td>
								<td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{adminUser.email}</td>
								<td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
									{adminUser.firstName} {adminUser.lastName}
								</td>
								<td class="px-6 py-4 whitespace-nowrap">
									<span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full {adminUser.isActive ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}">
										{adminUser.isActive ? $_('admin.active') : $_('admin.inactive')}
									</span>
								</td>
								<td class="px-6 py-4 whitespace-nowrap">
									{#if adminUser.isAdmin}
										<span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-purple-100 text-purple-800">
											{$_('admin.admin')}
										</span>
									{:else}
										<span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-gray-100 text-gray-800">
											{$_('admin.user')}
										</span>
									{/if}
								</td>
								<td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
									<div class="flex gap-2">
										{#if adminUser.isActive}
											<button
												on:click={() => showConfirm('deactivate', adminUser.id, adminUser.username)}
												disabled={adminUser.isSelf}
												class="btn btn-primary text-xs disabled:bg-gray-100 disabled:text-[steelblue] disabled:border-[steelblue] disabled:opacity-100"
												title={adminUser.isSelf ? $_('admin.cannotDeactivateSelf') : $_('admin.deactivateUser')}
											>
												{$_('admin.deactivate')}
											</button>
										{:else}
											<button
												on:click={() => showConfirm('activate', adminUser.id, adminUser.username)}
												class="btn btn-primary text-xs"
												title={$_('admin.activateUser')}
											>
												{$_('admin.activate')}
											</button>
										{/if}
										<button
											on:click={() => showConfirm('delete', adminUser.id, adminUser.username)}
											disabled={adminUser.isSelf}
											class="btn btn-primary text-xs disabled:bg-gray-100 disabled:text-[steelblue] disabled:border-[steelblue] disabled:opacity-100"
											title={adminUser.isSelf ? $_('admin.cannotDeleteSelf') : $_('admin.deleteUser')}
										>
											{$_('admin.delete')}
										</button>
									</div>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</div>
	{/if}
</div>

{#if confirmDialog.show}
	<div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
		<div class="bg-white border border-black p-6 max-w-md w-full rounded-tr-lg rounded-bl-lg">
			<h3 class="text-xl font-bold mb-4">{$_('admin.confirmTitle', { values: { action: confirmDialog.action } })}</h3>
			<p class="mb-6">
				{$_('admin.confirmMessage', { values: { action: confirmDialog.action } })} <strong>{confirmDialog.username}</strong>?
				{#if confirmDialog.action === 'delete'}
					<span class="block mt-2 text-red-600 font-semibold">
						{$_('admin.deleteWarning')}
					</span>
				{/if}
			</p>
			<div class="flex gap-2">
				<button on:click={confirmAction} class="btn btn-primary flex-1">
					{$_('admin.confirm')}
				</button>
				<button on:click={cancelConfirm} class="btn flex-1">
					{$_('admin.cancel')}
				</button>
			</div>
		</div>
	</div>
{/if}

<div class="fixed top-[5.4rem] right-4 z-50 flex flex-col gap-2">
	{#each toasts as toast (toast.id)}
		<div class="animate-slide-up">
			<div class="p-4 rounded-lg shadow-lg border-2 bg-green-50 border-green-500 text-green-800">
				{toast.message}
			</div>
		</div>
	{/each}
</div>
