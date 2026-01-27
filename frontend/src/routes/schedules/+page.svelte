<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { page } from '$app/stores';
	import { userStore } from '$lib/stores/user';
	import { _ } from 'svelte-i18n';
	import {
		getSchedules,
		getMedicines,
		createSchedule,
		updateSchedule,
		deleteSchedule,
		type Schedule,
		type Medicine
	} from '$lib/api';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	let schedules: Schedule[] = [];
	let medicines: Medicine[] = [];
	let loading = true;
	let error = '';
	let showForm = false;
	let editingId: string | null = null;
	let formElement: HTMLElement;

	// Toast notification state - support multiple stacked toasts
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

	let formData = {
		medicineId: '',
		time: '',
		amount: '',
		daysOfWeek: ''
	};

	const dayOptions = [
		{ code: 'MO', label: 'Mon' },
		{ code: 'TU', label: 'Tue' },
		{ code: 'WE', label: 'Wed' },
		{ code: 'TH', label: 'Thu' },
		{ code: 'FR', label: 'Fri' },
		{ code: 'SA', label: 'Sat' },
		{ code: 'SU', label: 'Sun' }
	];

	let selectedDays: Set<string> = new Set();
	let allDays = false;

	function updateDaysOfWeek() {
		if (allDays) {
			formData.daysOfWeek = '';
			selectedDays = new Set();
		} else {
			formData.daysOfWeek = Array.from(selectedDays).join(',');
		}
	}

	function toggleDay(code: string) {
		if (selectedDays.has(code)) {
			selectedDays.delete(code);
		} else {
			selectedDays.add(code);
			allDays = false;
		}
		selectedDays = selectedDays;
		updateDaysOfWeek();
	}

	function toggleAllDays() {
		allDays = !allDays;
		if (allDays) {
			selectedDays = new Set();
		}
		updateDaysOfWeek();
	}

	function parseDaysOfWeek(daysStr: string) {
		selectedDays = new Set();
		allDays = false;
		if (!daysStr || daysStr.trim() === '') {
			allDays = true;
		} else {
			const days = daysStr.split(',').map(d => d.trim()).filter(d => d);
			selectedDays = new Set(days);
		}
	}

	function scrollToForm() {
		if (formElement) {
			formElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
		}
	}

	async function loadData() {
		if (!browser) return;
		if (!$userStore) {
			loading = false;
			return;
		}
		loading = true;
		error = '';
		try {
			[schedules, medicines] = await Promise.all([getSchedules(), getMedicines()]);
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to load data';
		} finally {
			loading = false;
		}
	}

	// Check for 'add' query parameter and auto-open form
	$: if (browser && $page.url.searchParams.get('add') === 'true' && !loading) {
		startCreate();
		// Clean up URL without reloading page
		const url = new URL(window.location.href);
		url.searchParams.delete('add');
		window.history.replaceState({}, '', url);
	}

	function startCreate() {
		editingId = null;
		formData = { medicineId: '', time: '', amount: '', daysOfWeek: '' };
		allDays = true;
		selectedDays = new Set();
		showForm = true;
		setTimeout(scrollToForm, 50);
	}

	function startEdit(schedule: Schedule) {
		editingId = schedule.id;
		formData = {
			medicineId: schedule.medicineId,
			time: schedule.time,
			amount: schedule.amount.toString(),
			daysOfWeek: schedule.daysOfWeek || ''
		};
		parseDaysOfWeek(schedule.daysOfWeek || '');
		showForm = true;
		setTimeout(scrollToForm, 50);
	}

	function cancelForm() {
		showForm = false;
		editingId = null;
		formData = { medicineId: '', time: '', amount: '', daysOfWeek: '' };
		allDays = true;
		selectedDays = new Set();
	}

	async function handleSubmit() {
		error = '';
		try {
			const schedule = {
				medicineId: formData.medicineId,
				time: formData.time,
				amount: parseFloat(formData.amount),
				daysOfWeek: formData.daysOfWeek || undefined
			};
			const medicineName = getMedicineName(schedule.medicineId);
			if (editingId) {
				await updateSchedule(editingId, { id: editingId, ...schedule });
				showToastNotification($_('schedules.updatedSchedule', { values: { medicine: medicineName } }));
			} else {
				await createSchedule(schedule);
				showToastNotification($_('schedules.createdSchedule', { values: { medicine: medicineName } }));
			}
			await loadData();
			cancelForm();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to save schedule';
		}
	}

	async function handleDelete(id: string) {
		if (!confirm($_('schedules.confirmDelete'))) return;
		error = '';
		try {
			await deleteSchedule(id);
			await loadData();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to delete schedule';
		}
	}

	function getMedicineName(medicineId: string): string {
		return medicines.find((m) => m.id === medicineId)?.name || 'Unknown';
	}

	function formatDaysOfWeek(daysStr: string): string {
		if (!daysStr || daysStr.trim() === '') {
			return $_('schedules.everyDay');
		}
		const days = daysStr.split(',').map(d => d.trim());
		const labels = days.map(code => {
			const day = dayOptions.find(d => d.code === code);
			return day ? $_(`schedules.${day.label.toLowerCase()}`) : code;
		});
		return labels.join(', ');
	}

	function groupSchedules() {
		const groups: { [key: string]: Schedule[] } = {};

		schedules.forEach(schedule => {
			const daysKey = schedule.daysOfWeek || '';
			const key = `${schedule.time}|${daysKey}`;
			if (!groups[key]) {
				groups[key] = [];
			}
			groups[key].push(schedule);
		});

		// Convert to array and sort by time
		return Object.entries(groups)
			.sort((a, b) => {
				const timeA = a[0].split('|')[0];
				const timeB = b[0].split('|')[0];
				return timeA.localeCompare(timeB);
			})
			.map(([key, scheduleList]) => {
				const [time, daysOfWeek] = key.split('|');
				return { time, daysOfWeek, schedules: scheduleList };
			});
	}

	let hasLoadedOnce = false;

	onMount(async () => {
		await loadData();
		hasLoadedOnce = true;
	});

	// Reload data when user logs in or out, but avoid duplicate load on initial mount
	$: if (browser && $userStore && hasLoadedOnce) {
		loadData();
	}
</script>

<svelte:head>
	<title>Schedules - Medicine Scheduler</title>
</svelte:head>

{#if !$userStore}
	<!-- Not logged in message -->
	<div class="max-w-2xl mx-auto mt-12">
		<div class="card text-center py-12">
			<h2 class="text-2xl font-bold mb-4">Authentication Required</h2>
			<p class="text-gray-600 mb-6">
				Please login or register to view and manage your schedules.
			</p>
		</div>
	</div>
{:else}
<div class="max-w-6xl">
	<div class="flex justify-between items-center mb-6">
		<h2 class="text-3xl font-bold">{$_('schedules.title')}</h2>
		<button on:click={startCreate} class="btn btn-primary" disabled={medicines.length === 0}>
			{$_('schedules.add')}
		</button>
	</div>

	{#if error}
		<div class="card bg-red-50 border-red-300 text-red-800 mb-4">
			<p>{error}</p>
		</div>
	{/if}

	{#if medicines.length === 0 && !loading}
		<div class="card text-center py-12">
			<p class="text-gray-600 mb-4">{$_('schedules.noMedicines')}</p>
			<a href="/medicines" class="btn btn-primary">{$_('schedules.addMedicinesFirst')}</a>
		</div>
	{:else}
		{#if showForm}
			<div class="card mb-6" bind:this={formElement}>
				<h3 class="text-xl font-bold mb-4">{editingId ? $_('schedules.edit') : $_('schedules.add')}</h3>
				<form on:submit|preventDefault={handleSubmit} class="space-y-4">
					<div>
						<label for="schedule-medicine" class="block mb-1 font-semibold">{$_('schedules.medicine')}</label>
						<select id="schedule-medicine" bind:value={formData.medicineId} class="input w-full" required>
							<option value="">{$_('schedules.selectMedicine')}</option>
							{#each medicines as medicine}
								<option value={medicine.id}>
									{medicine.name} ({medicine.dose}{medicine.unit})
								</option>
							{/each}
						</select>
					</div>
					<div>
						<label for="schedule-time" class="block mb-1 font-semibold">{$_('schedules.time')}</label>
						<input id="schedule-time" type="time" bind:value={formData.time} class="input w-full" required />
					</div>
					<div>
						<label for="schedule-amount" class="block mb-1 font-semibold">{$_('schedules.amountLabel')}</label>
						<input
							id="schedule-amount"
							type="number"
							step="0.01"
							bind:value={formData.amount}
							class="input w-full"
							required
						/>
					</div>
					<div>
						<label for="schedule-days" class="block mb-2 font-semibold">{$_('schedules.daysOfWeek')}</label>
						<div class="flex flex-wrap gap-3 mb-2">
							<label class="flex items-center gap-2 cursor-pointer">
								<input
									type="checkbox"
									checked={allDays}
									on:change={toggleAllDays}
									class="w-4 h-4"
								/>
								<span class="font-semibold">{$_('schedules.allDays')}</span>
							</label>
						</div>
						<div class="flex flex-wrap gap-3">
							{#each dayOptions as day}
								<label class="flex items-center gap-2 cursor-pointer">
									<input
										type="checkbox"
										checked={selectedDays.has(day.code)}
										on:change={() => toggleDay(day.code)}
										disabled={allDays}
										class="w-4 h-4"
									/>
									<span class:text-gray-400={allDays}>{$_(`schedules.${day.label.toLowerCase()}`)}</span>
								</label>
							{/each}
						</div>
					</div>
					<div class="flex gap-2">
						<button type="submit" class="btn btn-primary">{$_('common.save')}</button>
						<button type="button" on:click={cancelForm} class="btn">{$_('common.cancel')}</button>
					</div>
				</form>
			</div>
		{/if}

		{#if loading}
			<div class="text-center py-12">
				<p class="text-gray-600">{$_('common.loading')}</p>
			</div>
		{:else if schedules.length > 0}
			<div class="columns-1 md:columns-2 gap-4 space-y-4">
				{#each groupSchedules() as group}
					<div class="card break-inside-avoid mb-4">
						<div class="mb-4 pb-3 border-b border-gray-200">
							<h3 class="text-xl font-bold">{group.time}</h3>
							<p class="text-sm text-gray-600">{formatDaysOfWeek(group.daysOfWeek)}</p>
						</div>
						<div class="space-y-3">
							{#each group.schedules as schedule}
								{@const med = medicines.find(m => m.id === schedule.medicineId)}
								<div class="flex justify-between items-center bg-gray-50 p-3 rounded">
									<div class="flex-1">
										<p class="font-semibold">
											{med ? med.name : $_('schedules.medicineNotFound')}
											{med ? ` (${med.dose}${med.unit})` : ''}
										</p>
										<p class="text-sm text-gray-600">
											{schedule.amount} {$_('schedules.doses')}
											{med ? ` = ${schedule.amount * med.dose} ${med.unit}` : ''}
											{!med ? $_('schedules.medicineNotFound') : ''}
										</p>
									</div>
									<div class="flex gap-2">
										<button on:click={() => startEdit(schedule)} class="btn btn-edit text-sm">{$_('common.edit')}</button>
										<button on:click={() => handleDelete(schedule.id)} class="btn btn-edit text-sm">{$_('common.delete')}</button>
									</div>
								</div>
							{/each}
						</div>
					</div>
				{/each}
			</div>
		{:else}
			<div class="card text-center py-12">
				<p class="text-gray-600 mb-4">{$_('schedules.noSchedulesFound')}</p>
				<button on:click={startCreate} class="btn btn-primary">{$_('schedules.addFirstSchedule')}</button>
			</div>
		{/if}
	{/if}
</div>
{/if}

<!-- Toast Notifications - stacked -->
<div class="fixed top-[5.4rem] right-4 z-50 flex flex-col gap-2">
	{#each toasts as toast (toast.id)}
		<div class="animate-slide-up">
			<div class="p-4 rounded-lg shadow-lg border-2 bg-blue-50 border-blue-500 text-blue-800">
				{toast.message}
			</div>
		</div>
	{/each}
</div>
