<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { userStore } from '$lib/stores/user';
	import { getDailySchedule, getDosageHistories, getWeeklyAdherence, takeDose, deleteDosageHistory, getMedicineExpiry, getMedicines, getSchedules, type DailySchedule, type DosageHistory, type TimeSlot, type WeeklyAdherence, type Medicine, type MedicineExpiry, type Schedule } from '$lib/api';
	import { _ } from 'svelte-i18n';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	let dailySchedule: DailySchedule | null = null;
	let dosageHistories: DosageHistory[] = [];
	let weeklyAdherence: WeeklyAdherence | null = null;
	let medicineExpiry: MedicineExpiry[] = [];
	let medicines: Medicine[] = [];
	let schedules: Schedule[] = [];
	let suppressedExpiringIds: Set<string> = new Set();
	let loading = true;
	let expiryLoading = false;
	let error = '';
	let expiryError = '';
	let takingDose: { [key: string]: boolean } = {};

	// Toast notification state - support multiple stacked toasts
	interface Toast {
		id: number;
		message: string;
	}
	let toasts: Toast[] = [];
	let toastIdCounter = 0;

	// Load suppressed IDs from localStorage
	function loadSuppressedIds() {
		if (!browser) return;
		const stored = localStorage.getItem('suppressedExpiring');
		if (stored) {
			try {
				suppressedExpiringIds = new Set(JSON.parse(stored));
			} catch (e) {
				suppressedExpiringIds = new Set();
			}
		}
	}

	// Save suppressed IDs to localStorage
	function saveSuppressedIds() {
		if (!browser) return;
		localStorage.setItem('suppressedExpiring', JSON.stringify(Array.from(suppressedExpiringIds)));
	}

	// Calculate medicines expiring within 7 days
	$: expiringMedicines = medicineExpiry.filter(m => {
		if (!m.expiryDate) return false;
		const expiryDate = new Date(m.expiryDate);
		const now = new Date();
		const daysUntilExpiry = Math.ceil((expiryDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
		return daysUntilExpiry <= 7 && daysUntilExpiry >= 0;
	});

	// Filter out suppressed medicines from expiring list
	$: visibleExpiringMedicines = expiringMedicines.filter(m => !suppressedExpiringIds.has(m.id));

	// Suppress current expiring medicines
	function suppressExpiringWarning() {
		expiringMedicines.forEach(m => suppressedExpiringIds.add(m.id));
		suppressedExpiringIds = suppressedExpiringIds; // Trigger reactivity
		saveSuppressedIds();
	}

	// Reactive day name mapping - recreates when language changes
	$: getDayName = (dayOfWeek: string): string => {
		const dayMap: { [key: string]: string } = {
			'MONDAY': $_('dashboard.monday'),
			'TUESDAY': $_('dashboard.tuesday'),
			'WEDNESDAY': $_('dashboard.wednesday'),
			'THURSDAY': $_('dashboard.thursday'),
			'FRIDAY': $_('dashboard.friday'),
			'SATURDAY': $_('dashboard.saturday'),
			'SUNDAY': $_('dashboard.sunday')
		};
		return dayMap[dayOfWeek] || dayOfWeek.substring(0, 3);
	};

	function showToastNotification(message: string) {
		const id = toastIdCounter++;
		toasts = [...toasts, { id, message }];
		setTimeout(() => {
			toasts = toasts.filter(t => t.id !== id);
		}, 6000);
	}

	async function loadSchedule() {
		if (!browser) return;
		if (!$userStore) {
			loading = false;
			return;
		}
		loading = true;
		error = '';
		try {
			[dailySchedule, dosageHistories, weeklyAdherence, medicines, schedules] = await Promise.all([
				getDailySchedule(),
				getDosageHistories(),
				getWeeklyAdherence(),
				getMedicines(),
				getSchedules()
			]);
			// Load medicine expiry separately after main data is loaded
			await loadMedicineExpiry();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to load schedule';
		} finally {
			loading = false;
		}
	}

	async function loadMedicineExpiry() {
		if (!browser) return;
		if (!$userStore) {
			expiryLoading = false;
			return;
		}
		expiryLoading = true;
		expiryError = '';
		try {
			medicineExpiry = await getMedicineExpiry();
		} catch (e) {
			expiryError = e instanceof Error ? e.message : 'Failed to load expiry data';
		} finally {
			expiryLoading = false;
		}
	}

	function isTakenToday(medicineId: string, scheduledTime: string): boolean {
		const today = new Date();
		today.setHours(0, 0, 0, 0);

		return dosageHistories.some(history => {
			const historyDate = new Date(history.datetime);
			const historyDateOnly = new Date(historyDate);
			historyDateOnly.setHours(0, 0, 0, 0);

			return history.medicineId === medicineId &&
				historyDateOnly.getTime() === today.getTime() &&
				history.scheduledTime === scheduledTime;
		});
	}

	async function handleTakeDose(medicineId: string, amount: number, medicineName: string, scheduledTime: string) {
		const key = `${medicineId}-${amount}`;
		takingDose[key] = true;
		try {
			await takeDose(medicineId, amount, scheduledTime);
			showToastNotification($_('dashboard.recordedDose', { values: { amount, medicine: medicineName } }));
			await Promise.all([loadSchedule(), loadMedicineExpiry()]);
		} catch (e) {
			error = e instanceof Error ? e.message : $_('dashboard.failedToRecord');
		} finally {
			takingDose[key] = false;
		}
	}

	async function takeAllForTimeSlot(timeSlot: TimeSlot) {
		const medicinesToTake = timeSlot.medicines.filter(item =>
			!isTakenToday(item.medicine.id, timeSlot.time) &&
			item.medicine.stock >= item.amount
		);

		if (medicinesToTake.length === 0) {
			showToastNotification($_('dashboard.noMedicinesToTake'));
			return;
		}

		try {
			for (const item of medicinesToTake) {
				const key = `${item.medicine.id}-${item.amount}`;
				takingDose[key] = true;
			}

			for (const item of medicinesToTake) {
				await takeDose(item.medicine.id, item.amount, timeSlot.time);
			}

			const medicineNames = medicinesToTake.map(item => item.medicine.name).join(', ');
			showToastNotification($_('dashboard.recordedMedicines', { values: { medicines: medicineNames } }));
			await Promise.all([loadSchedule(), loadMedicineExpiry()]);
		} catch (e) {
			error = e instanceof Error ? e.message : $_('dashboard.failedToRecordDoses');
		} finally {
			for (const item of medicinesToTake) {
				const key = `${item.medicine.id}-${item.amount}`;
				takingDose[key] = false;
			}
		}
	}

	async function handleUndoTimeSlot(scheduledTime: string) {
		try {
			// Find all dosage history entries for today with this scheduled time
			const today = new Date();
			today.setHours(0, 0, 0, 0);

			const dosageHistoriesToUndo = dosageHistories.filter(history => {
				const historyDate = new Date(history.datetime);
				historyDate.setHours(0, 0, 0, 0);

				return historyDate.getTime() === today.getTime() &&
					history.scheduledTime === scheduledTime;
			});

			if (dosageHistoriesToUndo.length === 0) {
				error = $_('dashboard.noDosesFound');
				return;
			}

			// Delete all dosage histories for this time slot
			for (const history of dosageHistoriesToUndo) {
				await deleteDosageHistory(history.id);
			}

			showToastNotification($_('dashboard.undone', { values: { count: dosageHistoriesToUndo.length, time: scheduledTime } }));
			await Promise.all([loadSchedule(), loadMedicineExpiry()]);
		} catch (e) {
			error = e instanceof Error ? e.message : $_('dashboard.failedToUndo');
		}
	}

	onMount(() => {
		loadSchedule();
		loadSuppressedIds();
		loadMedicineExpiry();
	});

	// Reload data when user logs in or out
	$: if (browser && $userStore) {
		loadSchedule();
		loadMedicineExpiry();
	}
</script>

<svelte:head>
	<title>Dashboard - Medicine Scheduler</title>
</svelte:head>

{#if !$userStore}
	<!-- Not logged in message -->
	<div class="max-w-2xl mx-auto mt-12">
		<div class="card text-center py-12">
			<div class="mb-6">
				<svg class="w-24 h-24 mx-auto text-[steelblue]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
				</svg>
			</div>
			<h2 class="text-3xl font-bold mb-4">{$_('dashboard.welcomeTitle')}</h2>
			<p class="text-gray-600 mb-6 text-lg">
				{$_('dashboard.welcomeSubtitle')}
			</p>
			<p class="text-gray-700 mb-8">
				{@html $_('dashboard.pleaseLogin')}
			</p>
		</div>
	</div>
{:else}
<div class="max-w-4xl">
	<!-- Medicine Expiring Warning Banner -->
	{#if !loading && visibleExpiringMedicines.length > 0}
		<div class="bg-yellow-50 border-2 border-yellow-400 rounded-lg mb-6 p-4">
			<div class="flex items-start gap-3">
				<svg class="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
					<path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
				</svg>
				<div class="flex-1">
					<p class="font-semibold text-yellow-800">
						Running Low: {visibleExpiringMedicines.length} medicine{visibleExpiringMedicines.length > 1 ? 's' : ''} expiring within 7 days
					</p>
					<div class="text-sm text-yellow-700 mt-1">
						{#each visibleExpiringMedicines as medicine, i}
							<span>
								<strong>{medicine.name}</strong> {medicine.dose} {medicine.unit}
								({medicine.stock} remaining, expires {medicine.expiryDate ? new Date(medicine.expiryDate).toLocaleDateString() : 'soon'}){#if i < visibleExpiringMedicines.length - 1}, {/if}
							</span>
						{/each}
					</div>
				</div>
				<button
					on:click={suppressExpiringWarning}
					class="text-yellow-700 hover:text-yellow-900 transition-colors flex-shrink-0"
					title="Dismiss this warning"
				>
					<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
					</svg>
				</button>
			</div>
		</div>
	{/if}

	<!-- Weekly Adherence Tracker -->
	{#if !loading && weeklyAdherence && weeklyAdherence.days.length > 0}
		<div class="mb-6 bg-white p-4">
			<div class="flex justify-between items-center gap-2">
				{#each weeklyAdherence.days as day}
					<div class="flex flex-col items-center flex-1">
						<div class="text-xs font-medium text-gray-600 mb-1">
							{getDayName(day.dayOfWeek)}
						</div>
						<div class="text-xs text-gray-500 mb-2">
							{day.dayNumber}/{day.month}
						</div>
						<a href={`/history?date=${day.date}`} class="relative w-12 h-12 block group" title="{day.takenCount}/{day.expectedCount} medications taken">
							{#if day.status === 'NONE'}
								<!-- Empty circle -->
								<svg class="w-12 h-12 group-hover:scale-105 transition-transform" viewBox="0 0 48 48">
									<circle cx="24" cy="24" r="22" fill="white" stroke="#D1D5DB" stroke-width="2"/>
								</svg>
							{:else if day.status === 'PARTIAL'}
								<!-- Half-filled circle -->
								<svg class="w-12 h-12 group-hover:scale-105 transition-transform" viewBox="0 0 48 48">
									<circle cx="24" cy="24" r="22" fill="white" stroke="#3B82F6" stroke-width="2"/>
									<path d="M 2,24 A 22,22 0 0,0 46,24 Z" fill="#3B82F6"/>
								</svg>
							{:else if day.status === 'COMPLETE'}
								<!-- Full circle with checkmark -->
								<svg class="w-12 h-12 group-hover:scale-105 transition-transform" viewBox="0 0 48 48">
									<circle cx="24" cy="24" r="22" fill="#10B981" stroke="#10B981" stroke-width="2"/>
									<path d="M 12 24 L 20 32 L 36 16" stroke="white" stroke-width="3" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
								</svg>
							{/if}
						</a>
					</div>
				{/each}
			</div>
		</div>
	{/if}

	<div class="flex justify-between items-center mb-6">
		<h2 class="text-2xl font-bold">{$_('dashboard.dailySchedule')}</h2>
	</div>

	{#if error}
		<div class="card bg-red-50 border-red-300 text-red-800 mb-4">
			<p>{error}</p>
		</div>
	{/if}

	{#if loading}
		<div class="text-center py-12">
			<p class="text-gray-600">{$_('dashboard.loadingSchedule')}</p>
		</div>
	{:else if dailySchedule && dailySchedule.schedule && dailySchedule.schedule.length > 0}
		<div class="columns-1 md:columns-2 gap-4 space-y-4">
			{#each dailySchedule.schedule as timeSlot}
				{@const allTaken = timeSlot.medicines.every(item => isTakenToday(item.medicine.id, timeSlot.time))}
				<div class="card break-inside-avoid mb-4">
					<div class="flex justify-between items-center mb-4 border-b border-gray-200 pb-2">
						<h3 class="text-xl font-bold">
							{timeSlot.time}
						</h3>
						<div class="flex gap-2">
							{#if allTaken}
								<button class="btn btn-taken ml-0 cursor-not-allowed" disabled>
									{$_('dashboard.allTaken')}
								</button>
								<button
									on:click={() => handleUndoTimeSlot(timeSlot.time)}
									class="p-1.5 text-gray-600 hover:text-gray-900 hover:bg-gray-200 rounded-full transition-colors"
									title={$_('dashboard.undo')}
								>
									<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
										<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6"/>
									</svg>
								</button>
							{:else}
								<button
									on:click={() => takeAllForTimeSlot(timeSlot)}
									class="btn btn-action"
								>
									{$_('dashboard.takeAll')}
								</button>
							{/if}
						</div>
					</div>
					<div class="space-y-3">
						{#each timeSlot.medicines as item}
							{@const key = `${item.medicine.id}-${item.amount}`}
							{@const takenToday = isTakenToday(item.medicine.id, timeSlot.time)}
							<div class="flex items-center justify-between border-b border-gray-100 pb-3 last:border-0">
								<div class="flex-1">
									<p class="font-semibold">{item.medicine.name}</p>
									<p class="text-sm text-gray-600">
										{item.amount}x {item.medicine.dose}{item.medicine.unit}
										{#if item.medicine.stock < item.amount}
											<span class="text-red-600 font-semibold ml-2">
												WARNING: {$_('dashboard.lowStock')} ({item.medicine.stock} {$_('dashboard.left')})
											</span>
										{:else}
											<span class="text-gray-500 ml-2">
												({item.medicine.stock} {$_('dashboard.inStock')})
											</span>
										{/if}
									</p>
								</div>
								{#if takenToday}
									<button class="btn btn-taken ml-4 cursor-not-allowed" disabled>
										{$_('dashboard.taken')}
									</button>
								{:else}
									<button
										on:click={() => handleTakeDose(item.medicine.id, item.amount, item.medicine.name, timeSlot.time)}
										class="btn btn-action ml-4"
										disabled={takingDose[key] || item.medicine.stock < item.amount}
									>
										{takingDose[key] ? 'Recording...' : $_('dashboard.take')}
									</button>
								{/if}
							</div>
						{/each}
					</div>
				</div>
			{/each}
		</div>
	{:else}
		<div class="card text-center py-12">
			{#if medicines.length === 0}
				<!-- No medicines at all -->
				<p class="text-gray-600 mb-2 text-lg font-semibold">{$_('dashboard.welcomeTitle')}!</p>
				<p class="text-gray-500 mb-4">Get started by adding your first medicine</p>
				<a href="/medicines?add=true" class="btn btn-primary">
					{$_('medicines.add')}
				</a>
			{:else if schedules.length === 0}
				<!-- Has medicines but no schedules -->
				<p class="text-gray-600 mb-2 text-lg font-semibold">You have {medicines.length} medicine{medicines.length !== 1 ? 's' : ''}</p>
				<p class="text-gray-500 mb-4">Create a schedule to get started with reminders</p>
				<a href="/schedules?add=true" class="btn btn-primary">
					{$_('schedules.add')}
				</a>
			{:else}
				<!-- Has both medicines and schedules, but no schedule for today -->
				<p class="text-gray-600 mb-4">{$_('dashboard.noSchedule')}</p>
				<a href="/schedules?add=true" class="btn btn-primary">{$_('schedules.add')}</a>
			{/if}
		</div>
	{/if}

	<!-- Medicine Expiry Forecast -->
	{#if !expiryLoading && medicineExpiry.length > 0}
		<div class="mt-10">
			<h2 class="text-xl font-bold mb-2">{$_('dashboard.expiryForecast')}</h2>
			<div class="overflow-x-auto">
				<table class="min-w-full bg-white border border-gray-200 rounded-lg">
					<thead>
						<tr class="bg-gray-100">
							<th class="px-4 py-2 text-left">{$_('dashboard.expiryTableName')}</th>
							<th class="px-4 py-2 text-left">{$_('dashboard.expiryTableDose')}</th>
							<th class="px-4 py-2 text-left">{$_('dashboard.expiryTableStock')}</th>
							<th class="px-4 py-2 text-left">{$_('dashboard.expiryTableExpiry')}</th>
						</tr>
					</thead>
					<tbody>
						{#each medicineExpiry as med}
							<tr>
								<td class="px-4 py-2 font-medium">{med.name}</td>
								<td class="px-4 py-2">{med.dose} {med.unit}</td>
								<td class="px-4 py-2">{med.stock}</td>
								{#if med.expiryDate}
									{@const expiry = new Date(med.expiryDate)}
									{@const now = new Date()}
									{@const daysLeft = Math.ceil((expiry.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))}
									<td class="px-4 py-2 {daysLeft < 7 ? 'text-red-600 font-bold' : daysLeft < 14 ? 'text-yellow-600 font-semibold' : ''}">
										{expiry.toLocaleDateString()}
									</td>
								{:else}
									<td class="px-4 py-2">-</td>
								{/if}
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</div>
	{:else if expiryLoading}
		<div class="mt-10 text-gray-500">{$_('dashboard.loadingExpiryForecast')}</div>
	{:else if expiryError}
		<div class="mt-10 text-red-600">{expiryError}</div>
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
