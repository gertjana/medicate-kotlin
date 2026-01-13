<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { userStore } from '$lib/stores/user';
	import { getDailySchedule, getDosageHistories, getWeeklyAdherence, getLowStockMedicines, takeDose, type DailySchedule, type DosageHistory, type TimeSlot, type WeeklyAdherence, type Medicine } from '$lib/api';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	let dailySchedule: DailySchedule | null = null;
	let dosageHistories: DosageHistory[] = [];
	let weeklyAdherence: WeeklyAdherence | null = null;
	let lowStockMedicines: Medicine[] = [];
	let suppressedLowStockIds: Set<string> = new Set();
	let loading = true;
	let error = '';
	let takingDose: { [key: string]: boolean } = {};
	let toastMessage = '';
	let showToast = false;

	// Load suppressed IDs from localStorage
	function loadSuppressedIds() {
		if (!browser) return;
		const stored = localStorage.getItem('suppressedLowStock');
		if (stored) {
			try {
				suppressedLowStockIds = new Set(JSON.parse(stored));
			} catch (e) {
				suppressedLowStockIds = new Set();
			}
		}
	}

	// Save suppressed IDs to localStorage
	function saveSuppressedIds() {
		if (!browser) return;
		localStorage.setItem('suppressedLowStock', JSON.stringify(Array.from(suppressedLowStockIds)));
	}

	// Filter out suppressed medicines from low stock list
	$: visibleLowStockMedicines = lowStockMedicines.filter(m => !suppressedLowStockIds.has(m.id));

	// Suppress current low stock medicines
	function suppressLowStockWarning() {
		lowStockMedicines.forEach(m => suppressedLowStockIds.add(m.id));
		suppressedLowStockIds = suppressedLowStockIds; // Trigger reactivity
		saveSuppressedIds();
	}

	function getDayName(dayOfWeek: string): string {
		const dayMap: { [key: string]: string } = {
			'MONDAY': 'Mon',
			'TUESDAY': 'Tue',
			'WEDNESDAY': 'Wed',
			'THURSDAY': 'Thu',
			'FRIDAY': 'Fri',
			'SATURDAY': 'Sat',
			'SUNDAY': 'Sun'
		};
		return dayMap[dayOfWeek] || dayOfWeek.substring(0, 3);
	}

	function showToastNotification(message: string) {
		toastMessage = message;
		showToast = true;
		setTimeout(() => {
			showToast = false;
		}, 3000);
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
			[dailySchedule, dosageHistories, weeklyAdherence, lowStockMedicines] = await Promise.all([
				getDailySchedule(),
				getDosageHistories(),
				getWeeklyAdherence(),
				getLowStockMedicines(10)
			]);
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to load schedule';
		} finally {
			loading = false;
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
			showToastNotification(`Recorded: ${amount}x ${medicineName}`);
			await loadSchedule();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to record dose';
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
			showToastNotification('No medicines to take at this time');
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
			showToastNotification(`Recorded: ${medicineNames}`);
			await loadSchedule();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to record doses';
		} finally {
			for (const item of medicinesToTake) {
				const key = `${item.medicine.id}-${item.amount}`;
				takingDose[key] = false;
			}
		}
	}

	onMount(loadSchedule);
	onMount(loadSuppressedIds);

	// Reload data when user logs in or out
	$: if (browser && $userStore) {
		loadSchedule();
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
			<h2 class="text-3xl font-bold mb-4">Welcome to Medicate</h2>
			<p class="text-gray-600 mb-6 text-lg">
				Your personal medicine tracking assistant
			</p>
			<p class="text-gray-700 mb-8">
				Please <strong>login</strong> or <strong>register</strong> to start tracking your medicines, schedules, and adherence.
			</p>
			<div class="text-sm text-gray-500">
				<p class="mb-2">✓ Track your medicines and dosages</p>
				<p class="mb-2">✓ Create medication schedules</p>
				<p class="mb-2">✓ Monitor adherence and history</p>
				<p>✓ Get low stock alerts</p>
			</div>
		</div>
	</div>
{:else}
<div class="max-w-4xl">
	<!-- Low Stock Warning Banner -->
	{#if !loading && visibleLowStockMedicines.length > 0}
		<div class="bg-yellow-50 border-2 border-yellow-400 rounded-lg mb-6 p-4">
			<div class="flex items-start gap-3">
				<svg class="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
					<path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
				</svg>
				<div class="flex-1">
					<p class="font-semibold text-yellow-800">
						Low Stock Warning: {visibleLowStockMedicines.length} medicine{visibleLowStockMedicines.length > 1 ? 's' : ''} running low
					</p>
					<div class="text-sm text-yellow-700 mt-1">
						{#each visibleLowStockMedicines as medicine, i}
							<span>
								<strong>{medicine.name}</strong> {medicine.dose} {medicine.unit} ({medicine.stock} remaining){#if i < visibleLowStockMedicines.length - 1}, {/if}
							</span>
						{/each}
					</div>
				</div>
				<button
					on:click={suppressLowStockWarning}
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
		<h2 class="text-2xl font-bold">Today's Schedule</h2>
	</div>

	{#if error}
		<div class="card bg-red-50 border-red-300 text-red-800 mb-4">
			<p>{error}</p>
		</div>
	{/if}

	{#if loading}
		<div class="text-center py-12">
			<p class="text-gray-600">Loading schedule...</p>
		</div>
	{:else if dailySchedule && dailySchedule.schedule && dailySchedule.schedule.length > 0}
		<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
			{#each dailySchedule.schedule as timeSlot}
				{@const allTaken = timeSlot.medicines.every(item => isTakenToday(item.medicine.id, timeSlot.time))}
				<div class="card">
					<div class="flex justify-between items-center mb-4 border-b border-gray-200 pb-2">
						<h3 class="text-xl font-bold">
							{timeSlot.time}
						</h3>
						{#if allTaken}
							<button class="btn btn-taken ml-0 cursor-not-allowed" disabled>
								✓ All Taken
							</button>
						{:else}
							<button
								on:click={() => takeAllForTimeSlot(timeSlot)}
								class="btn btn-action"
							>
								Take All
							</button>
						{/if}
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
												⚠ Low stock ({item.medicine.stock} left)
											</span>
										{:else}
											<span class="text-gray-500 ml-2">
												({item.medicine.stock} in stock)
											</span>
										{/if}
									</p>
								</div>
								{#if takenToday}
									<button class="btn btn-taken ml-4 cursor-not-allowed" disabled>
										✓ Taken
									</button>
								{:else}
									<button
										on:click={() => handleTakeDose(item.medicine.id, item.amount, item.medicine.name, timeSlot.time)}
										class="btn btn-action ml-4"
										disabled={takingDose[key] || item.medicine.stock < item.amount}
									>
										{takingDose[key] ? 'Recording...' : 'Take Dose'}
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
			<p class="text-gray-600 mb-4">No scheduled medicines for today</p>
			<a href="/schedules" class="btn btn-primary">Add Schedule</a>
		</div>
	{/if}
</div>
{/if}

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
