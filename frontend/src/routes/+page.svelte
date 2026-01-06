<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { getDailySchedule, getDosageHistories, takeDose, type DailySchedule, type DosageHistory, type TimeSlot } from '$lib/api';

	export let data = {};
	export let params = {};

	let dailySchedule: DailySchedule | null = null;
	let dosageHistories: DosageHistory[] = [];
	let loading = true;
	let error = '';
	let takingDose: { [key: string]: boolean } = {};
	let toastMessage = '';
	let showToast = false;

	function showToastNotification(message: string) {
		toastMessage = message;
		showToast = true;
		setTimeout(() => {
			showToast = false;
		}, 3000);
	}

	async function loadSchedule() {
		if (!browser) return;
		loading = true;
		error = '';
		try {
			[dailySchedule, dosageHistories] = await Promise.all([
				getDailySchedule(),
				getDosageHistories()
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
</script>

<svelte:head>
	<title>Dashboard - Medicine Scheduler</title>
</svelte:head>

<div class="max-w-4xl">
	<div class="flex justify-between items-center mb-6">
		<h2 class="text-3xl font-bold">Today's Schedule</h2>
		<button on:click={loadSchedule} class="btn" disabled={loading}>
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
			<p class="text-gray-600">Loading schedule...</p>
		</div>
	{:else if dailySchedule && dailySchedule.schedule && dailySchedule.schedule.length > 0}
		<div class="space-y-6">
			{#each dailySchedule.schedule as timeSlot}
				{@const allTaken = timeSlot.medicines.every(item => isTakenToday(item.medicine.id, timeSlot.time))}
				<div class="card">
					<div class="flex justify-between items-center mb-4 border-b border-gray-200 pb-2">
						<h3 class="text-xl font-bold">
							{timeSlot.time}
						</h3>
						{#if allTaken}
							<button class="btn opacity-50 cursor-not-allowed" disabled>
								✓ All Taken
							</button>
						{:else}
							<button 
								on:click={() => takeAllForTimeSlot(timeSlot)}
								class="btn btn-primary"
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
										{#if takenToday}
											<span class="text-green-600 font-semibold ml-2">✓ Taken today</span>
										{/if}
									</p>
								</div>
								{#if takenToday}
									<button class="btn ml-4 opacity-50 cursor-not-allowed" disabled>
										✓ Taken
									</button>
								{:else}
									<button
										on:click={() => handleTakeDose(item.medicine.id, item.amount, item.medicine.name, timeSlot.time)}
										class="btn btn-primary ml-4"
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

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
