<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { getDosageHistories, getMedicines, getSchedules, takeDose, type DosageHistory, type Medicine, type Schedule } from '$lib/api';

	export let data = {};
	export let params = {};

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

	function groupHistories() {
		// Day of week mapping
		const dayOfWeekCodes = ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'];
		
		// Get unique scheduled times
		const scheduledTimes = Array.from(new Set(schedules.map(s => s.time))).sort((a, b) => b.localeCompare(a));

		// Generate last 7 days (excluding today)
		const last7Days: Date[] = [];
		for (let i = 1; i <= 7; i++) {
			const date = new Date();
			date.setDate(date.getDate() - i);
			date.setHours(0, 0, 0, 0);
			last7Days.push(date);
		}

		groupedHistories = last7Days.map(dateObj => {
			const dateKey = dateObj.toLocaleDateString();
			const dayOfWeek = dayOfWeekCodes[dateObj.getDay()];

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
						// 2. No daysOfWeek specified (applies to all days), OR
						// 3. daysOfWeek is empty string (applies to all days), OR
						// 4. daysOfWeek contains this day of week
						if (s.time !== time) return false;
						if (!s.daysOfWeek || s.daysOfWeek.trim() === '') return true;
						const scheduleDays = s.daysOfWeek.split(',').map(d => d.trim());
						return scheduleDays.includes(dayOfWeek);
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
				date: dateKey,
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

	onMount(loadData);
</script>

<svelte:head>
	<title>History - Medicine Scheduler</title>
</svelte:head>

<div class="max-w-6xl">
	<div class="flex justify-between items-center mb-6">
		<h2 class="text-3xl font-bold">Dosage History</h2>
		<button on:click={loadData} class="btn" disabled={loading}>
			{loading ? 'Loading...' : 'Refresh'}
		</button>
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
		<div class="card overflow-x-auto">
			<table class="w-full">
				<thead>
					<tr class="border-b border-gray-200">
						<th class="text-left py-2 px-4 font-semibold text-sm">Date</th>
						<th class="text-left py-2 px-4 font-semibold text-sm">Time</th>
						<th class="text-left py-2 px-4 font-semibold text-sm">Medicine</th>
					</tr>
				</thead>
				<tbody>
					{#each groupedHistories as dateGroup, dateIndex}
						{@const totalDateRows = dateGroup.timeSlots.reduce((sum, ts) => sum + (ts.isMissing ? 1 : ts.histories.length), 0)}
						{#each dateGroup.timeSlots as timeSlot, timeIndex}
							{#if timeSlot.isMissing}
								<tr class="border-b border-gray-100 bg-yellow-50">
									{#if timeIndex === 0}
										<td class="py-2 px-4 align-top text-sm border-r border-gray-200" rowspan={totalDateRows}>
											{dateGroup.date}
										</td>
									{/if}
									<td class="py-2 px-4 text-sm text-gray-700 border-r border-gray-200">
										{timeSlot.time}
									</td>
									<td class="py-2 px-4">
										<button 
											on:click={() => takeAllMissing(dateGroup.dateObj, timeSlot.time, timeSlot.scheduledMedicines || [])}
											class="btn btn-primary"
										>
											Take All (Missing)
										</button>
									</td>
								</tr>
							{:else}
								{#each timeSlot.histories as history, historyIndex}
									{@const medicine = medicines.find(m => m.id === history.medicineId)}
									<tr class="border-b border-gray-100 hover:bg-gray-50">
										{#if timeIndex === 0 && historyIndex === 0}
											<td class="py-2 px-4 align-top text-sm border-r border-gray-200" rowspan={totalDateRows}>
												{dateGroup.date}
											</td>
										{/if}
										{#if historyIndex === 0}
											<td class="py-2 px-4 align-top text-sm text-gray-700 border-r border-gray-200" rowspan={timeSlot.histories.length}>
												{timeSlot.time}
											</td>
										{/if}
										<td class="py-2 px-4">
											<span class="text-sm">
												{history.amount}x {getMedicineName(history.medicineId)}
												{#if medicine}
													<span class="text-gray-600">({medicine.dose}{medicine.unit})</span>
												{/if}
											</span>
										</td>
									</tr>
								{/each}
							{/if}
						{/each}
					{/each}
				</tbody>
			</table>
		</div>
	{:else}
		<div class="card text-center py-12">
			<p class="text-gray-600 mb-4">No dosage history found</p>
			<a href="/" class="btn btn-primary">Go to Dashboard</a>
		</div>
	{/if}
</div>

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
