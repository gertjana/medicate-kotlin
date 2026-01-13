<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { userStore } from '$lib/stores/user';
	import { getDosageHistories, getMedicines, getSchedules, takeDose, type DosageHistory, type Medicine, type Schedule } from '$lib/api';
	import { page } from '$app/stores';
	import { tick } from 'svelte';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	let histories: DosageHistory[] = [];
	let medicines: Medicine[] = [];
	let schedules: Schedule[] = [];
	let loading = true;
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

	interface GroupedHistory {
		date: string;
		dateObj: Date;
		timeSlots: {
			time: string;
			histories: DosageHistory[];
			isMissing: boolean;
			scheduledMedicines?: { medicineId: string; amount: number }[];
		}[];
	}

	let groupedHistories: GroupedHistory[] = [];

	async function loadData() {
		if (!browser) return;
		if (!$userStore) {
			loading = false;
			return;
		}
		loading = true;
		error = '';
		try {
			[histories, medicines, schedules] = await Promise.all([getDosageHistories(), getMedicines(), getSchedules()]);
			groupHistories();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to load history';
		} finally {
			loading = false;
		}
	}

	function toLocalIsoDate(date: Date): string {
		const year = date.getFullYear();
		const month = String(date.getMonth() + 1).padStart(2, '0');
		const day = String(date.getDate()).padStart(2, '0');
		return `${year}-${month}-${day}`;
	}

	// In groupHistories(), set date to ISO string (YYYY-MM-DD)
	function groupHistories() {
		// Day of week mapping - JavaScript getDay() returns 0=Sunday, 1=Monday, etc.
		// Map to two-letter codes that match backend DayOfWeek enum
		const dayCodeMap = ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'];

		// Get unique scheduled times
		const scheduledTimes = Array.from(new Set(schedules.map(s => s.time))).sort((a, b) => b.localeCompare(a));

		// Generate last 7 days (yesterday to 7 days ago)
		const last7Days: Date[] = [];
		for (let i = 1; i <= 7; i++) {
			const date = new Date();
			date.setDate(date.getDate() - i);
			date.setHours(0, 0, 0, 0);
			last7Days.push(new Date(date));
		}

		groupedHistories = last7Days.map(dateObj => {
			const isoDate = toLocalIsoDate(dateObj); // use local date for card id and matching
			const dayCode = dayCodeMap[dateObj.getDay()]; // Get two-letter code like "MO"

			// For each scheduled time, check if doses were taken
			const timeSlots = scheduledTimes.map(time => {
				// Find histories for this date and time
				const matchingHistories = histories.filter(h => {
					const historyDate = new Date(h.datetime);
					historyDate.setHours(0, 0, 0, 0);
					return historyDate.getTime() === dateObj.getTime() && h.scheduledTime === time;
				});

				// Get scheduled medicines for this time, filtered by day of week
				const scheduledMedicines = schedules
					.filter(s => {
						// Include schedule if:
						// 1. Time matches
						// 2. daysOfWeek is not set or empty string (applies to all days), OR
						// 3. daysOfWeek contains this day code
						if (s.time !== time) return false;
						if (!s.daysOfWeek || s.daysOfWeek.trim() === '') return true;
						const scheduleDays = s.daysOfWeek.split(',').map(d => d.trim());
						return scheduleDays.includes(dayCode);
					})
					.map(s => ({ medicineId: s.medicineId, amount: s.amount }));

				const isMissing = matchingHistories.length === 0 && scheduledMedicines.length > 0;

				return {
					time,
					histories: matchingHistories,
					isMissing,
					scheduledMedicines
				};
			}).filter(ts => ts.histories.length > 0 || ts.isMissing);

			return {
				date: isoDate,
				dateObj,
				timeSlots
			};
		}).filter(day => day.timeSlots.length > 0);
	}

	async function takeAllMissing(dateObj: Date, time: string, scheduledMedicines: { medicineId: string; amount: number }[]) {
		try {
			// Parse scheduled time to construct datetime
			const [hours, minutes] = time.split(':').map(Number);
			const doseDatetime = new Date(dateObj);
			doseDatetime.setHours(hours, minutes, 0, 0);

			// Format as ISO local datetime (without timezone): "2026-01-05T08:00:00"
			const year = doseDatetime.getFullYear();
			const month = String(doseDatetime.getMonth() + 1).padStart(2, '0');
			const day = String(doseDatetime.getDate()).padStart(2, '0');
			const hour = String(doseDatetime.getHours()).padStart(2, '0');
			const minute = String(doseDatetime.getMinutes()).padStart(2, '0');
			const second = String(doseDatetime.getSeconds()).padStart(2, '0');
			const datetimeString = `${year}-${month}-${day}T${hour}:${minute}:${second}`;

			for (const med of scheduledMedicines) {
				await takeDose(med.medicineId, med.amount, time, datetimeString);
			}
			const medicineNames = scheduledMedicines.map(m => getMedicineName(m.medicineId)).join(', ');
			showToastNotification(`Recorded: ${medicineNames}`);
			await loadData();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to record doses';
		}
	}

	function getMedicineName(medicineId: string): string {
		return medicines.find((m) => m.id === medicineId)?.name || 'Unknown Medicine';
	}

	function formatDateTime(datetime: string): string {
		const date = new Date(datetime);
		return date.toLocaleString();
	}

	onMount(async () => {
		await loadData();
		// Scroll to the day if ?date=YYYY-MM-DD is present
		const url = new URL(window.location.href);
		const dateParam = url.searchParams.get('date');
		if (dateParam) {
			await tick();
			const el = document.getElementById('history-day-' + dateParam);
			if (el) {
				el.scrollIntoView({ behavior: 'smooth', block: 'center' });
				el.classList.add('ring-2', 'ring-blue-400');
				setTimeout(() => el.classList.remove('ring-2', 'ring-blue-400'), 2000);
			}
		}
	});

	// Reload data when user logs in or out
	$: if (browser && $userStore) {
		loadData();
	}
</script>

<svelte:head>
	<title>History - Medicine Scheduler</title>
</svelte:head>

{#if !$userStore}
	<!-- Not logged in message -->
	<div class="max-w-2xl mx-auto mt-12">
		<div class="card text-center py-12">
			<h2 class="text-2xl font-bold mb-4">Authentication Required</h2>
			<p class="text-gray-600 mb-6">
				Please login or register to view your dosage history.
			</p>
		</div>
	</div>
{:else}
<div class="max-w-6xl">
	<div class="flex justify-between items-center mb-6">
		<h2 class="text-3xl font-bold">Dosage History</h2>
	</div>

	{#if error}
		<div class="card bg-red-50 border-red-300 text-red-800 mb-4">
			<p>{error}</p>
		</div>
	{/if}

	{#if loading}
		<div class="text-center py-12">
			<p class="text-gray-600">Loading history...</p>
		</div>
	{:else if groupedHistories.length > 0}
		<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
			{#each groupedHistories as dateGroup}
				<div class="card" id={`history-day-${dateGroup.date}`}> <!-- use local date for id -->
					<div class="mb-4 pb-0 border-b border-gray-200">
						<h3 class="text-xl font-bold">{dateGroup.date}</h3>
					</div>
					<div class="space-y-4">
						{#each dateGroup.timeSlots as timeSlot}
							{#if timeSlot.isMissing}
								<div class="bg-yellow-50 border border-yellow-200 rounded p-3">
									<div class="flex justify-between items-center mb-2">
										<span class="font-semibold text-gray-700">{timeSlot.time}</span>
										<span class="text-xs text-yellow-700 bg-yellow-100 px-2 py-1 rounded">Missed</span>
									</div>
									<div class="text-sm text-yellow-800 mb-2">
										{#each timeSlot.scheduledMedicines || [] as med}
											<div>{med.amount}x {getMedicineName(med.medicineId)}</div>
										{/each}
									</div>
									<button
										on:click={() => takeAllMissing(dateGroup.dateObj, timeSlot.time, timeSlot.scheduledMedicines || [])}
										class="btn btn-action w-full text-sm"
									>
										Take All
									</button>
								</div>
							{:else if timeSlot.histories.length > 0}
								<div class="bg-gray-50 rounded p-3">
									<div class="font-semibold text-gray-700 mb-2">{timeSlot.time}</div>
									<div class="space-y-2">
										{#each timeSlot.histories as history}
											{@const medicine = medicines.find(m => m.id === history.medicineId)}
											<div class="text-sm">
												<span class="font-medium">{history.amount}x {getMedicineName(history.medicineId)}</span>
												{#if medicine}
													<span class="text-gray-600">({medicine.dose}{medicine.unit})</span>
												{/if}
											</div>
										{/each}
									</div>
								</div>
							{/if}
						{/each}
					</div>
				</div>
			{/each}
		</div>
	{:else}
		<div class="card text-center py-12">
			<p class="text-gray-600 mb-4">No dosage history found</p>
			<a href="/" class="btn btn-primary">Go to Dashboard</a>
		</div>
	{/if}
</div>
{/if}

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
