<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { page } from '$app/stores';
	import { userStore } from '$lib/stores/user';
	import {
		getMedicines,
		createMedicine,
		updateMedicine,
		deleteMedicine,
		addStock,
		searchMedicines,
		type Medicine,
		type MedicineSearchResult
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
		name: '',
		dose: '',
		unit: '',
		stock: '',
		description: '',
		bijsluiter: ''
	};

	// Autocomplete state
	let searchResults: MedicineSearchResult[] = [];
	let showDropdown = false;
	let searchTimeout: number;
	let nameInput: HTMLInputElement;
	let selectedIndex = -1; // -1 means no selection, use typed value
	let dropdownElement: HTMLDivElement;
	const MAX_VISIBLE_RESULTS = 5; // Threshold for showing the navigation hint when many results are returned

	function scrollToForm() {
		if (formElement) {
			formElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
		}
	}

	async function handleNameInput(event: Event) {
		const input = event.target as HTMLInputElement;
		const value = input.value;
		formData.name = value;

		clearTimeout(searchTimeout);

		if (value.length < 2) {
			searchResults = [];
			showDropdown = false;
			selectedIndex = -1;
			return;
		}

		searchTimeout = window.setTimeout(async () => {
			try {
				searchResults = await searchMedicines(value);
				showDropdown = searchResults.length > 0;
				selectedIndex = -1; // Reset selection when results change
			} catch (e) {
				console.error('Failed to search medicines:', e);
				showToastNotification('Failed to search medicines. Please try again.');
			}
		}, 300);
	}

	function selectMedicine(result: MedicineSearchResult) {
		formData.name = result.productnaam;
		formData.bijsluiter = result.bijsluiter_filenaam || '';
		searchResults = [];
		showDropdown = false;
		selectedIndex = -1;
		if (nameInput) {
			nameInput.blur();
		}
	}

	function hideDropdown() {
		setTimeout(() => {
			showDropdown = false;
			selectedIndex = -1;
		}, 200);
	}

	function handleKeyDown(event: KeyboardEvent) {
		if (!showDropdown || searchResults.length === 0) return;

		switch (event.key) {
			case 'ArrowDown':
				event.preventDefault();
				selectedIndex = Math.min(selectedIndex + 1, searchResults.length - 1);
				scrollToSelected();
				break;
			case 'ArrowUp':
				event.preventDefault();
				selectedIndex = Math.max(selectedIndex - 1, -1);
				scrollToSelected();
				break;
			case 'Enter':
				event.preventDefault();
				if (selectedIndex >= 0 && selectedIndex < searchResults.length) {
					selectMedicine(searchResults[selectedIndex]);
				}
				break;
			case 'Escape':
				event.preventDefault();
				showDropdown = false;
				selectedIndex = -1;
				break;
		}
	}

	function scrollToSelected() {
		if (!dropdownElement || selectedIndex < 0) return;

		const selectedElement = dropdownElement.children[selectedIndex] as HTMLElement;
		if (selectedElement) {
			// Scroll the selected element into view
			selectedElement.scrollIntoView({
				block: 'nearest',
				behavior: 'smooth'
			});
		}
	}

	async function loadMedicines() {
		if (!browser) return;
		if (!$userStore) {
			loading = false;
			return;
		}
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
		formData = { name: '', dose: '', unit: '', stock: '', description: '', bijsluiter: '' };
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
			description: medicine.description || '',
			bijsluiter: medicine.bijsluiter || ''
		};
		showForm = true;
		setTimeout(scrollToForm, 50);
	}

	function cancelForm() {
		showForm = false;
		editingId = null;
		formData = { name: '', dose: '', unit: '', stock: '', description: '', bijsluiter: '' };
	}

	async function handleSubmit() {
		error = '';
		try {
			const medicine = {
				name: formData.name,
				dose: parseFloat(formData.dose),
				unit: formData.unit,
				stock: parseFloat(formData.stock),
				description: formData.description || undefined,
				bijsluiter: formData.bijsluiter || undefined
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

	// Reload data when user logs in or out
	$: if (browser && $userStore) {
		loadMedicines();
	}

	$: sortedMedicines = [...medicines].sort((a, b) => a.name.localeCompare(b.name));
</script>

<svelte:head>
	<title>Medicines - Medicine Scheduler</title>
</svelte:head>

{#if !$userStore}
	<!-- Not logged in message -->
	<div class="max-w-2xl mx-auto mt-12">
		<div class="card text-center py-12">
			<h2 class="text-2xl font-bold mb-4">Authentication Required</h2>
			<p class="text-gray-600 mb-6">
				Please login or register to view and manage your medicines.
			</p>
		</div>
	</div>
{:else}
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
				<div class="relative">
					<label for="medicine-name" class="block mb-1 font-semibold">Name</label>
					<input
						id="medicine-name"
						type="text"
						bind:this={nameInput}
						value={formData.name}
						on:input={handleNameInput}
						on:keydown={handleKeyDown}
						on:blur={hideDropdown}
						class="input w-full"
						autocomplete="off"
						required
					/>
				{#if showDropdown && searchResults.length > 0}
					<div
						bind:this={dropdownElement}
						class="absolute z-50 w-full mt-1 bg-white border-2 border-gray-300 shadow-lg rounded-md max-h-60 overflow-y-auto"
					>
						{#each searchResults as result, index}
							<button
								type="button"
								on:mousedown={() => selectMedicine(result)}
								on:mouseenter={() => selectedIndex = index}
								class="w-full text-left px-4 py-2 border-b border-gray-200 last:border-b-0 cursor-pointer transition-colors {index === selectedIndex ? 'bg-blue-100' : 'hover:bg-blue-50'}"
							>
								<div class="font-semibold text-gray-900">{result.productnaam}</div>
								<div class="text-sm text-gray-600">
									{result.farmaceutischevorm}
									{#if result.werkzamestoffen}
										- {result.werkzamestoffen}
									{/if}
								</div>
							</button>
						{/each}
						{#if searchResults.length >= MAX_VISIBLE_RESULTS}
							<div class="px-4 py-2 text-xs text-gray-500 text-center border-t border-gray-200 bg-gray-50">
								{searchResults.length} result{searchResults.length > 1 ? 's' : ''} - Use ↑/↓ to navigate
							</div>
						{/if}
					</div>
				{/if}
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
				<div>
					<label for="medicine-bijsluiter" class="block mb-1 font-semibold">Package Leaflet URL (Optional)</label>
					<input
						id="medicine-bijsluiter"
						type="url"
						bind:value={formData.bijsluiter}
						class="input w-full"
						placeholder="Automatically filled when selecting from database..."
						readonly={formData.bijsluiter !== ''}
					/>
					{#if formData.bijsluiter}
						<p class="text-xs text-gray-500 mt-1">
							<a
								href={formData.bijsluiter}
								target="_blank"
								rel="noopener noreferrer"
								class="text-blue-600 hover:text-blue-800 underline"
							>
								View package leaflet
							</a>
						</p>
					{/if}
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
	{:else if sortedMedicines.length > 0}
		<div class="grid grid-cols-1 md:grid-cols-2 gap-4">
			{#each sortedMedicines as medicine}
				<div class="card flex flex-col relative">
					<div class="flex justify-between items-start mb-4">
						<h3 class="text-xl font-bold">{medicine.name}</h3>
					{#if medicine.bijsluiter}
					<a
						href={medicine.bijsluiter}
						target="_blank"
						rel="noopener noreferrer"
						class="text-[steelblue] hover:text-[#4682b4]"
						title="View package leaflet (PDF)"
					>
						<svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12" fill="currentColor" viewBox="0 0 24 24">
							<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z"/>
							<path fill="#ffffff" d="M14 2v6h6"/>
							<text x="7" y="19" font-size="6" font-weight="bold" fill="#ffffff">PDF</text>
						</svg>
					</a>
					{/if}
					</div>
					<div class="flex-1 pb-8">
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
						<button on:click={() => startEdit(medicine)} class="btn btn-edit text-sm px-3 py-1">Edit</button>
						<button on:click={() => handleDelete(medicine.id, medicine.name)} class="btn btn-edit text-sm px-3 py-1">
							Delete
						</button>
						<button on:click={() => openStockModal(medicine.id)} class="btn btn-edit text-sm px-3 py-1">
							+ Stock
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
					<button type="submit" class="btn btn-edit">Add Stock</button>
					<button type="button" on:click={closeStockModal} class="btn">Cancel</button>
				</div>
			</form>
		</div>
	</div>
{/if}
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
