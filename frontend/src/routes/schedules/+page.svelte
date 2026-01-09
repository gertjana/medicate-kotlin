<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { userStore } from '$lib/stores/user';
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
	let toastMessage = '';
	let showToast = false;

	function showToastNotification(message: string) {
		toastMessage = message;
		showToast = true;
		setTimeout(() => {
			showToast = false;
		}, 3000);
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
				showToastNotification(`Updated schedule for ${medicineName}`);
			} else {
				await createSchedule(schedule);
				showToastNotification(`Created schedule for ${medicineName}`);
			}
			await loadData();
			cancelForm();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to save schedule';
		}
	}

	async function handleDelete(id: string) {
		if (!confirm('Delete this schedule?')) return;
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
			return 'Every day';
		}
		const days = daysStr.split(',').map(d => d.trim());
		const labels = days.map(code => {
			const day = dayOptions.find(d => d.code === code);
			return day ? day.label : code;
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
		<h2 class="text-3xl font-bold">Schedules</h2>
		<button on:click={startCreate} class="btn btn-primary" disabled={medicines.length === 0}>
			Add Schedule
		</button>
	</div>

	{#if error}
		<div class="card bg-red-50 border-red-300 text-red-800 mb-4">
			<p>{error}</p>
		</div>
	{/if}

	{#if medicines.length === 0 && !loading}
		<div class="card text-center py-12">
			<p class="text-gray-600 mb-4">No medicines available</p>
			<a href="/medicines" class="btn btn-primary">Add medicines first</a>
		</div>
	{:else}
		{#if showForm}
			<div class="card mb-6" bind:this={formElement}>
				<h3 class="text-xl font-bold mb-4">{editingId ? 'Edit' : 'Add'} Schedule</h3>
				<form on:submit|preventDefault={handleSubmit} class="space-y-4">
					<div>
						<label for="schedule-medicine" class="block mb-1 font-semibold">Medicine</label>
						<select id="schedule-medicine" bind:value={formData.medicineId} class="input w-full" required>
							<option value="">Select a medicine</option>
							{#each medicines as medicine}
								<option value={medicine.id}>
									{medicine.name} ({medicine.dose}{medicine.unit})
								</option>
							{/each}
						</select>
					</div>
					<div>
						<label for="schedule-time" class="block mb-1 font-semibold">Time</label>
						<input id="schedule-time" type="time" bind:value={formData.time} class="input w-full" required />
					</div>
					<div>
						<label for="schedule-amount" class="block mb-1 font-semibold">Amount (number of doses)</label>
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
						<label for="schedule-days" class="block mb-2 font-semibold">Days of Week</label>
						<div class="flex flex-wrap gap-3 mb-2">
							<label class="flex items-center gap-2 cursor-pointer">
								<input
									type="checkbox"
									checked={allDays}
									on:change={toggleAllDays}
									class="w-4 h-4"
								/>
								<span class="font-semibold">All Days</span>
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
									<span class:text-gray-400={allDays}>{day.label}</span>
								</label>
							{/each}
						</div>
					</div>
					<div class="flex gap-2">
						<button type="submit" class="btn btn-primary">Save</button>
						<button type="button" on:click={cancelForm} class="btn">Cancel</button>
					</div>
				</form>
			</div>
		{/if}

		{#if loading}
			<div class="text-center py-12">
				<p class="text-gray-600">Loading schedules...</p>
			</div>
		{:else if schedules.length > 0}
			<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
				{#each groupSchedules() as group}
					<div class="card">
						<div class="mb-4 pb-3 border-b border-gray-200">
							<h3 class="text-xl font-bold">{group.time}</h3>
							<p class="text-sm text-gray-600">{formatDaysOfWeek(group.daysOfWeek)}</p>
						</div>
						<div class="space-y-3">
							{#each group.schedules as schedule}
								<div class="flex justify-between items-center bg-gray-50 p-3 rounded">
									<div class="flex-1">
										<p class="font-semibold">{getMedicineName(schedule.medicineId)}</p>
										<p class="text-sm text-gray-600">{schedule.amount} dose(s)</p>
									</div>
									<div class="flex gap-2">
										<button on:click={() => startEdit(schedule)} class="btn text-sm">Edit</button>
										<button on:click={() => handleDelete(schedule.id)} class="btn text-sm">Delete</button>
									</div>
								</div>
							{/each}
						</div>
					</div>
				{/each}
			</div>
		{:else}
			<div class="card text-center py-12">
				<p class="text-gray-600 mb-4">No schedules found</p>
				<button on:click={startCreate} class="btn btn-primary">Add your first schedule</button>
			</div>
		{/if}
	{/if}
</div>
{/if}

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
