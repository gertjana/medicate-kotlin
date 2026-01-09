<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import {
		getMedicines,
		createMedicine,
		updateMedicine,
		deleteMedicine,
		addStock,
		type Medicine
	} from '$lib/api';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	let medicines: Medicine[] = [];
	let loading = true;
	let error = '';
	let showForm = false;
	let editingId: string | null = null;
	let showStockModal = false;
	let stockMedicineId = '';
	let stockAmount = '';
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
		name: '',
		dose: '',
		unit: '',
		stock: '',
		description: ''
	};

	function scrollToForm() {
		if (formElement) {
			formElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
		}
	}

	async function loadMedicines() {
		if (!browser) return;
		loading = true;
		error = '';
		try {
			const data = await getMedicines();
			medicines = [...data]; // Force reactivity
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to load medicines';
		} finally {
			loading = false;
		}
	}

	function startCreate() {
		editingId = null;
		formData = { name: '', dose: '', unit: '', stock: '', description: '' };
		showForm = true;
		setTimeout(scrollToForm, 50);
	}

	function startEdit(medicine: Medicine) {
		editingId = medicine.id;
		formData = {
			name: medicine.name,
			dose: medicine.dose.toString(),
			unit: medicine.unit,
			stock: medicine.stock.toString(),
			description: medicine.description || ''
		};
		showForm = true;
		setTimeout(scrollToForm, 50);
	}

	function cancelForm() {
		showForm = false;
		editingId = null;
		formData = { name: '', dose: '', unit: '', stock: '', description: '' };
	}

	async function handleSubmit() {
		error = '';
		try {
			const medicine = {
				name: formData.name,
				dose: parseFloat(formData.dose),
				unit: formData.unit,
				stock: parseFloat(formData.stock),
				description: formData.description || undefined
			};

			if (editingId) {
				await updateMedicine(editingId, { id: editingId, ...medicine });
			} else {
				await createMedicine(medicine);
			}
			await loadMedicines();
			cancelForm();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to save medicine';
		}
	}

	async function handleDelete(id: string, name: string) {
		if (!confirm(`Delete ${name}?`)) return;
		error = '';
		try {
			await deleteMedicine(id);
			await loadMedicines();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to delete medicine';
		}
	}

	function openStockModal(id: string) {
		stockMedicineId = id;
		stockAmount = '';
		showStockModal = true;
	}

	function closeStockModal() {
		showStockModal = false;
		stockMedicineId = '';
		stockAmount = '';
	}

	async function handleAddStock() {
		error = '';
		try {
			const medicine = medicines.find(m => m.id === stockMedicineId);
			await addStock(stockMedicineId, parseFloat(stockAmount));
			await loadMedicines();
			showToastNotification(`Added ${stockAmount} to ${medicine?.name || 'medicine'}`);
			closeStockModal();
		} catch (e) {
			error = e instanceof Error ? e.message : 'Failed to add stock';
		}
	}

	onMount(loadMedicines);
</script>

<svelte:head>
	<title>Medicines - Medicine Scheduler</title>
</svelte:head>

<div class="max-w-6xl">
	<div class="flex justify-between items-center mb-6">
		<h2 class="text-3xl font-bold">Medicines</h2>
		<button on:click={startCreate} class="btn btn-primary">Add Medicine</button>
	</div>

	{#if error}
		<div class="card bg-red-50 border-red-300 text-red-800 mb-4">
			<p>{error}</p>
		</div>
	{/if}

	{#if showForm}
		<div class="card mb-6" bind:this={formElement}>
			<h3 class="text-xl font-bold mb-4">{editingId ? 'Edit' : 'Add'} Medicine</h3>
			<form on:submit|preventDefault={handleSubmit} class="space-y-4">
				<div>
					<label for="medicine-name" class="block mb-1 font-semibold">Name</label>
					<input id="medicine-name" type="text" bind:value={formData.name} class="input w-full" required />
				</div>
				<div class="grid grid-cols-2 gap-4">
					<div>
						<label for="medicine-dose" class="block mb-1 font-semibold">Dose</label>
						<input
							id="medicine-dose"
							type="number"
							step="0.01"
							bind:value={formData.dose}
							class="input w-full"
							required
						/>
					</div>
					<div>
						<label for="medicine-unit" class="block mb-1 font-semibold">Unit</label>
						<input id="medicine-unit" type="text" bind:value={formData.unit} class="input w-full" required />
					</div>
				</div>
				<div>
					<label for="medicine-stock" class="block mb-1 font-semibold">Stock</label>
					<input
						id="medicine-stock"
						type="number"
						step="0.01"
						bind:value={formData.stock}
						class="input w-full"
						required
					/>
				</div>
				<div>
					<label for="medicine-description" class="block mb-1 font-semibold">Description (Optional)</label>
					<textarea
						id="medicine-description"
						bind:value={formData.description}
						class="input w-full"
						rows="3"
						placeholder="Enter medicine description..."
					/>
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
			<p class="text-gray-600">Loading medicines...</p>
		</div>
	{:else if medicines.length > 0}
		<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
			{#each medicines as medicine}
				<div class="card flex flex-col">
					<div class="flex justify-between items-start mb-4">
						<h3 class="text-xl font-bold">{medicine.name}</h3>
						<button on:click={() => openStockModal(medicine.id)} class="btn btn-primary text-sm px-3 py-1">
							+ Stock
						</button>
					</div>
					<div class="flex-1">
						<p class="text-gray-600">
							{medicine.dose}{medicine.unit} per dose
						</p>
						{#if medicine.description}
							<p class="text-sm text-gray-600 mt-2 italic">
								{medicine.description}
							</p>
						{/if}
						<p class="mt-2">
							<span class="font-semibold">Stock:</span>
							<span class={medicine.stock <= 10 ? 'text-red-600 font-semibold' : ''}>
								{medicine.stock}
							</span>
							{#if medicine.stock <= 10}
								<span class="text-red-600 ml-2">⚠ Low stock</span>
							{/if}
						</p>
					</div>
					<div class="flex gap-2 mt-4">
						<button on:click={() => startEdit(medicine)} class="btn flex-1">Edit</button>
						<button on:click={() => handleDelete(medicine.id, medicine.name)} class="btn flex-1">
							Delete
						</button>
					</div>
				</div>
			{/each}
		</div>
	{:else}
		<div class="card text-center py-12">
			<p class="text-gray-600 mb-4">No medicines found</p>
			<button on:click={startCreate} class="btn btn-primary">Add your first medicine</button>
		</div>
	{/if}
</div>

<!-- Stock Modal -->
{#if showStockModal}
	<div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4">
		<div class="bg-white border border-black p-6 max-w-md w-full">
			<h3 class="text-xl font-bold mb-4">Add Stock</h3>
			<form on:submit|preventDefault={handleAddStock}>
				<div class="mb-4">
					<label for="stock-amount" class="block mb-1 font-semibold">Amount to add</label>
					<input
						id="stock-amount"
						type="number"
						step="0.01"
						bind:value={stockAmount}
						class="input w-full"
						required
					/>
				</div>
				<div class="flex gap-2">
					<button type="submit" class="btn btn-primary">Add Stock</button>
					<button type="button" on:click={closeStockModal} class="btn">Cancel</button>
				</div>
			</form>
		</div>
	</div>
{/if}

{#if showToast}
	<div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
		{toastMessage}
	</div>
{/if}
